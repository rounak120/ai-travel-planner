package com.aibot.travelplanner;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.AuthorityUtils;

/** Existing integration scenarios now exercise the real security chain as a fixture user. */
public class TestIdentityListener extends AbstractTestExecutionListener {
    @Override public void beforeTestMethod(TestContext context) {
        var application = context.getApplicationContext();
        if (!java.util.Arrays.asList(application.getEnvironment().getActiveProfiles()).contains("test"))
            throw new IllegalStateException("Test identities require the test profile");
        var jdbc=application.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        if(jdbc.queryForObject("SELECT count(*) FROM app_users WHERE username='test_fixture'",Integer.class)==0) {
            var encoder=application.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
            jdbc.update("INSERT INTO app_users(username,password_hash) VALUES (?,?) ON CONFLICT DO NOTHING",
                    "test_fixture",encoder.encode(TestAuth.PASSWORD));
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test_fixture",null,AuthorityUtils.createAuthorityList("ROLE_USER")));
    }
    @Override public void afterTestMethod(TestContext context) {SecurityContextHolder.clearContext();}
}
