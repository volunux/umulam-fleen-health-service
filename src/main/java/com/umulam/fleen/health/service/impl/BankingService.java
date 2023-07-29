package com.umulam.fleen.health.service.impl;

import com.umulam.fleen.health.adapter.paystack.PaystackAdapter;
import com.umulam.fleen.health.adapter.paystack.model.enums.RecipientType;
import com.umulam.fleen.health.adapter.paystack.model.request.CreateTransferRecipientRequest;
import com.umulam.fleen.health.adapter.paystack.model.request.CreateTransferRecipientRequest.CreateTransferRecipientMetadata;
import com.umulam.fleen.health.adapter.paystack.model.request.ResolveBankAccountRequest;
import com.umulam.fleen.health.adapter.paystack.response.CreateTransferRecipientResponse;
import com.umulam.fleen.health.adapter.paystack.response.GetBanksResponse;
import com.umulam.fleen.health.adapter.paystack.response.ResolveBankAccountResponse;
import com.umulam.fleen.health.constant.session.CurrencyType;
import com.umulam.fleen.health.exception.banking.BankAccountAlreadyExists;
import com.umulam.fleen.health.exception.banking.BankAccountNotFoundException;
import com.umulam.fleen.health.exception.banking.InvalidAccountTypeCombinationException;
import com.umulam.fleen.health.exception.banking.InvalidBankCodeException;
import com.umulam.fleen.health.model.domain.Member;
import com.umulam.fleen.health.model.domain.MemberBankAccount;
import com.umulam.fleen.health.model.dto.banking.AddBankAccountDto;
import com.umulam.fleen.health.model.response.member.GetMemberUpdateDetailsResponse;
import com.umulam.fleen.health.model.security.FleenUser;
import com.umulam.fleen.health.repository.jpa.BankAccountJpaRepository;
import com.umulam.fleen.health.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.umulam.fleen.health.adapter.paystack.response.GetBanksResponse.BankData;
import static com.umulam.fleen.health.constant.base.GeneralConstant.PAYSTACK_GET_BANKS_CACHE_PREFIX;
import static java.util.Objects.isNull;

@Slf4j
@Service
public class BankingService {

  private final PaystackAdapter paystackAdapter;
  private final CacheService cacheService;
  private final BankAccountJpaRepository bankAccountJpaRepository;
  private final MemberService memberService;

  public BankingService(PaystackAdapter paystackAdapter,
                        CacheService cacheService,
                        BankAccountJpaRepository bankAccountJpaRepository,
                        MemberService memberService) {
    this.paystackAdapter = paystackAdapter;
    this.cacheService = cacheService;
    this.bankAccountJpaRepository = bankAccountJpaRepository;
    this.memberService = memberService;
  }

  public List<BankData> getBanks(String currency) {
    String cacheKey = getBanksCacheKey().concat(currency.toUpperCase());
    if (cacheService.exists(cacheKey)) {
      return cacheService.get(cacheKey, GetBanksResponse.class).getData();
    }

    GetBanksResponse banksResponse = paystackAdapter.getBanks(currency);
    cacheService.set(cacheKey, banksResponse);
    return banksResponse.getData();
  }

  @Transactional
  public void addBankAccount(AddBankAccountDto dto, FleenUser user) {
    ResolveBankAccountRequest request = ResolveBankAccountRequest.builder()
      .accountNumber(dto.getAccountNumber())
      .bankCode(dto.getBankCode())
      .build();

    String recipientType = dto.getRecipientType().toUpperCase();
    String currency = dto.getCurrency().toUpperCase();

    if (!isBankCodeExists(dto.getBankCode(), currency)) {
      throw new InvalidBankCodeException(dto.getBankCode());
    }

    if (!isAccountTypeCombinationTrue(recipientType, currency)) {
      throw new InvalidAccountTypeCombinationException(recipientType, currency);
    }

    boolean accountNumberExist = bankAccountJpaRepository.existsByAccountNumber(dto.getAccountNumber());
    if (accountNumberExist) {
      throw new BankAccountAlreadyExists(dto.getAccountNumber());
    }

    ResolveBankAccountResponse bankAccountResponse = paystackAdapter.resolveBankAccount(request);
    GetMemberUpdateDetailsResponse member = memberService.getMemberGetUpdateDetailsResponse(user);
    CreateTransferRecipientRequest transferRecipientRequest = CreateTransferRecipientRequest.builder()
        .type(recipientType)
        .accountNumber(dto.getAccountNumber())
        .accountName(bankAccountResponse.getData().getAccountName())
        .bankCode(dto.getBankCode())
        .currency(currency)
        .build();

    CreateTransferRecipientMetadata metadata = CreateTransferRecipientMetadata.builder()
        .emailAddress(member.getEmailAddress())
        .phoneNumber(member.getPhoneNumber())
        .firstName(member.getFirstName())
        .lastName(member.getLastName())
        .build();
    transferRecipientRequest.setMetadata(metadata);
    CreateTransferRecipientResponse createRecipientResponse = paystackAdapter.createTransferRecipient(transferRecipientRequest);

    MemberBankAccount bankAccount = dto.toBankAccount();
    bankAccount.setAccountName(bankAccountResponse.getData().getAccountName());
    bankAccount.setBankName(createRecipientResponse.getData().getDetails().getBankName());
    bankAccount.setExternalSystemRecipientCode(createRecipientResponse.getData().getRecipientCode());
    bankAccount.setMember(Member.builder().id(user.getId()).build());
    bankAccountJpaRepository.save(bankAccount);
  }

  @Transactional
  public void deleteBankAccount(String accountNumber, FleenUser user) {
    Optional<MemberBankAccount> bankAccountExist = bankAccountJpaRepository.findByAccountNumberAndMember(accountNumber, Member.builder().id(user.getId()).build());
    if (bankAccountExist.isEmpty()) {
      throw new BankAccountNotFoundException(accountNumber);
    }

    MemberBankAccount bankAccount = bankAccountExist.get();
    String recipientCode = bankAccount.getExternalSystemRecipientCode();
    paystackAdapter.deleteTransferRecipient(recipientCode);
    bankAccountJpaRepository.delete(bankAccount);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void saveBanksToCacheOnStartup() {
    GetBanksResponse banksResponse = paystackAdapter.getBanks(CurrencyType.NGN.getValue());
    saveBanksToCache(banksResponse, null);
  }

  @Scheduled(cron = "0 0 */12 * * *")
  private void saveBanksToCache() {
    saveBanksToCache(null, CurrencyType.NGN.getValue());
  }

  private void saveBanksToCache(GetBanksResponse response, String currency) {
    if (isNull(response) || isNull(response.getData()) || response.getData().isEmpty()) {
      saveBanksToCacheOnStartup();
    }
    String key = getBanksCacheKey().concat(Objects.toString(currency, CurrencyType.NGN.getValue()));
    cacheService.set(key, response);
  }

  private String getBanksCacheKey() {
    return PAYSTACK_GET_BANKS_CACHE_PREFIX;
  }

  private boolean isBankCodeExists(String bankCode, String currency) {
    List<BankData> banks = getBanks(currency);
    return banks
      .stream()
      .anyMatch(bank -> bank.getCode().equalsIgnoreCase(bankCode)
                     && bank.getCurrency().equalsIgnoreCase(currency));
  }

  public static boolean isAccountTypeCombinationTrue(String recipientType, String currencyType) {
    RecipientType recipient = RecipientType.valueOf(recipientType.toUpperCase());
    CurrencyType currency = CurrencyType.valueOf(currencyType.toUpperCase());

    switch (recipient) {
      case NUBAN:
        return currency == CurrencyType.NGN;
      case MOBILE_MONEY:
        return currency == CurrencyType.GHS;
      case BASA:
        return currency == CurrencyType.ZAR;
      default:
        return false;
    }
  }

}