package com.capitalone.dashboard.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.Http401AuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.capitalone.dashboard.auth.AuthProperties;
import com.capitalone.dashboard.auth.AuthenticationResultHandler;
import com.capitalone.dashboard.auth.ldap.LdapLoginRequestFilter;
import com.capitalone.dashboard.auth.standard.StandardLoginRequestFilter;
import com.capitalone.dashboard.auth.token.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
	
	@Autowired
	private JwtAuthenticationFilter jwtAuthenticationFilter;
	
	@Autowired
	private AuthenticationResultHandler authenticationResultHandler;

	@Autowired
	private AuthenticationProvider standardAuthenticationProvider;
	
	@Autowired
	private AuthProperties authProperties;
	
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.headers().cacheControl();
		http.csrf().disable()
			.authorizeRequests().antMatchers("/appinfo").permitAll()
								.antMatchers("/registerUser").permitAll()
								.antMatchers("/login**").permitAll()
								.antMatchers(HttpMethod.GET, "/**").permitAll()
								.anyRequest().authenticated()
									.and()
								.addFilterBefore(standardLoginRequestFilter(), UsernamePasswordAuthenticationFilter.class)
								.addFilterBefore(ldapLoginRequestFilter(), UsernamePasswordAuthenticationFilter.class)
								.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
								.exceptionHandling().authenticationEntryPoint(new Http401AuthenticationEntryPoint("Authorization"));
	}
	
	@Override
	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		auth.authenticationProvider(standardAuthenticationProvider);
		
		 if (StringUtils.isNotBlank(authProperties.getLdapServerUrl())) {
			auth.ldapAuthentication()
			.userDnPatterns(authProperties.getLdapUserDnPattern())
			.contextSource().url(authProperties.getLdapServerUrl());
		}
	}
	
	@Bean
	protected StandardLoginRequestFilter standardLoginRequestFilter() throws Exception {
		return new StandardLoginRequestFilter("/login", authenticationManager(), authenticationResultHandler);
	}
	
	@Bean
	protected LdapLoginRequestFilter ldapLoginRequestFilter() throws Exception {
		return new LdapLoginRequestFilter("/login/ldap", authenticationManager(), authenticationResultHandler);
	}
	
}
