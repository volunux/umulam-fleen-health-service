package com.umulam.fleen.health.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umulam.fleen.health.constant.authentication.AuthenticationStatus;
import com.umulam.fleen.health.constant.authentication.TokenType;
import com.umulam.fleen.health.model.dto.authentication.JwtTokenDetails;
import com.umulam.fleen.health.model.security.FleenUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.umulam.fleen.health.constant.base.GeneralConstant.*;
import static com.umulam.fleen.health.util.FleenAuthorities.getRefreshTokenAuthorities;
import static com.umulam.fleen.health.util.FleenAuthorities.getResetPasswordAuthorities;

@Slf4j
@Component
@Getter
@Setter
@PropertySource("classpath:application.properties")
public class JwtProvider {

  private final ObjectMapper mapper;

  @Value("${fleen.health.jwt.issuer}")
  public String JWT_ISSUER;

  @Value("${fleen.health.jwt.secret}")
  public String JWT_SECRET;

  public JwtProvider(ObjectMapper objectMapper) {
    this.mapper = objectMapper;
  }

  public String getUsernameFromToken(String token) {
    return getClaimFromToken(token, Claims::getSubject);
  }

  public Date getExpirationDateFromToken(String token) {
    return getClaimFromToken(token, Claims::getExpiration);
  }

  public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = getAllClaimsFromToken(token);
    return claimsResolver.apply(claims);
  }

  public Object getClaim(String token, String key) {
    final Claims claims = getAllClaimsFromToken(token);
    return claims.get(key);
  }

  private Claims getAllClaimsFromToken(String token) {
    return Jwts
            .parser()
            .setSigningKey(JWT_SECRET)
            .parseClaimsJws(token)
            .getBody();
  }

  public Map<String, Object> getTokenDetails(String token) {
    return new HashMap<>(getAllClaimsFromToken(token));
  }

  public JwtTokenDetails getBasicDetails(String token) {
    return mapper.convertValue(getTokenDetails(token), JwtTokenDetails.class);
  }

  public boolean isTokenExpired(String token) {
    final Date expiration = getExpirationDateFromToken(token);
    return expiration.before(new Date());
  }

  public String generateToken(FleenUser user, TokenType tokenType) {
    Map<String, Object> claims = buildClaims(user);
    claims.put(TOKEN_TYPE_KEY, tokenType.getValue());

    return createToken(user.getUsername(), claims, ACCESS_TOKEN_VALIDITY);
  }

  public String generateToken(FleenUser user, TokenType tokenType, AuthenticationStatus authenticationStatus) {
    Map<String, Object> claims = buildClaims(user);
    claims.put(TOKEN_TYPE_KEY, tokenType.getValue());
    claims.put(AUTHENTICATION_STATUS_KEY, authenticationStatus.name());

    return createToken(user.getUsername(), claims, ACCESS_TOKEN_VALIDITY);
  }

  public String generateRefreshToken(FleenUser user, TokenType tokenType) {
    Map<String, Object> claims = new HashMap<>();
    claims.put(TOKEN_TYPE_KEY, tokenType.getValue());
    setBasicUserClaims(claims, user.getId(), authoritiesToList(getRefreshTokenAuthorities()));

    return createToken(user.getUsername(), claims, REFRESH_TOKEN_VALIDITY);
  }

  public String generateResetPasswordToken(FleenUser user) {
    Map<String, Object> claims = new HashMap<>();
    claims.put(TOKEN_TYPE_KEY, TokenType.RESET_PASSWORD);
    setBasicUserClaims(claims, user.getId(), authoritiesToList(getResetPasswordAuthorities()));

    return createToken(user.getUsername(), claims, RESET_PASSWORD_TOKEN_VALIDITY);
  }

  public String createToken(String subject, Map<String, Object> claims, long expirationPeriod) {
    return Jwts.builder()
            .setClaims(claims)
            .setSubject(subject)
            .setIssuer(JWT_ISSUER)
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(new Date(System.currentTimeMillis() + expirationPeriod))
            .signWith(SignatureAlgorithm.HS512, JWT_SECRET)
            .compact();
  }

  public boolean isTokenValid(String token, UserDetails details) {
    final String username = getUsernameFromToken(token);
    return (username.equals(details.getUsername()) && !isTokenExpired(token));
  }

  public Map<String, Object> buildClaims(FleenUser user) {
    Map<String, Object> claims = new HashMap<>();
    setBasicUserClaims(claims, user.getId(), authoritiesToList(user.getAuthorities()));

    claims.put("firstName", user.getFirstName());
    claims.put("lastName", user.getLastName());
    claims.put("emailAddress", user.getEmailAddress());
    claims.put("profilePhoto", user.getProfilePhoto());

    return claims;
  }

  private void setBasicUserClaims(Map<String, Object> claims, Long userId, String[] authorities) {
    claims.put(CLAIMS_USER_ID_KEY, userId);
    claims.put(CLAIMS_AUTHORITY_KEY, authorities);
  }

  public String[] authoritiesToList(Collection<? extends GrantedAuthority> authorities) {
    return authorities
            .stream()
            .map(GrantedAuthority::getAuthority)
            .toArray(String[]::new);
  }
}
