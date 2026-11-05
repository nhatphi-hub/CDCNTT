package com.smart_ecommerce.backend;

import com.jayway.jsonpath.JsonPath;
import com.smart_ecommerce.backend.config.SecurityConfig;
import com.smart_ecommerce.backend.controller.CartController;
import com.smart_ecommerce.backend.controller.OrderController;
import com.smart_ecommerce.backend.controller.StaffController;
import com.smart_ecommerce.backend.controller.TestController;
import com.smart_ecommerce.backend.controller.UserController;
import com.smart_ecommerce.backend.dto.cart.CartResponse;
import com.smart_ecommerce.backend.entity.Role;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.exception.ApiExceptionHandler;
import com.smart_ecommerce.backend.repository.RoleRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import com.smart_ecommerce.backend.security.JwtAuthenticationFilter;
import com.smart_ecommerce.backend.security.RestSecurityExceptionHandler;
import com.smart_ecommerce.backend.service.CartService;
import com.smart_ecommerce.backend.service.GoogleAuthService;
import com.smart_ecommerce.backend.service.JwtService;
import com.smart_ecommerce.backend.service.OrderService;
import com.smart_ecommerce.backend.service.StaffService;
import com.smart_ecommerce.backend.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real MVC controllers and security chain with in-memory repository mocks.
 * No Boot application, auto-configuration, JPA, datasource, or startup initializer is loaded.
 */
class SecurityRegressionTests {

    private static final String TEST_PASSWORD = "Regression-test-password";
    private static final String TEST_PASSWORD_HASH = new BCryptPasswordEncoder().encode(TEST_PASSWORD);
    private static final long EXPIRATION_MS = 60_000L;
    private static final UUID CUSTOMER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STAFF_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private CartService cartService;
    private OrderService orderService;
    private StaffService staffService;
    private JwtService jwtService;
    private final Map<UUID, User> usersById = new HashMap<>();
    private final Map<String, User> usersByEmail = new HashMap<>();

    @BeforeEach
    void createIsolatedSecurityMvcContext() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "security-regression-test-properties", Map.of(
                        "app.jwt.secret", encodedTestKey(1),
                        "app.jwt.expiration", Long.toString(EXPIRATION_MS),
                        "app.cors.allowed-origins", "http://localhost:5173")));
        context.register(IsolatedSecurityMvcConfiguration.class);
        context.refresh();

        userRepository = context.getBean(UserRepository.class);
        roleRepository = context.getBean(RoleRepository.class);
        cartService = context.getBean(CartService.class);
        orderService = context.getBean(OrderService.class);
        staffService = context.getBean(StaffService.class);
        jwtService = context.getBean(JwtService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        addUser(CUSTOMER_ID, "customer@example.test", "CUSTOMER");
        addUser(STAFF_ID, "staff@example.test", "STAFF");
        addUser(ADMIN_ID, "admin@example.test", "ADMIN");
        when(userRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(usersById.get(invocation.getArgument(0))));
        when(userRepository.findByEmailIgnoreCase(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(usersByEmail.get(
                        invocation.<String>getArgument(0).toLowerCase(java.util.Locale.ROOT))));
        when(roleRepository.findByNameIgnoreCase("CUSTOMER"))
                .thenReturn(Optional.of(Role.builder().name("CUSTOMER").build()));
        when(cartService.getCart(CUSTOMER_ID)).thenReturn(new CartResponse(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                CUSTOMER_ID, List.of(), 0, BigDecimal.ZERO));
        when(orderService.getOrdersForManagement()).thenReturn(List.of());
        when(staffService.getAllStaff()).thenReturn(List.of());

        assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        assertFalse(context.containsBean("roleDataInitializer"));
    }

    @AfterEach
    void closeIsolatedContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void healthRouteIsPublicWithoutABearerToken() throws Exception {
        mvc.perform(get("/api/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("Smart Ecommerce Backend is running!"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void publicRegistrationCreatesOnlyACustomerAndReturnsNoPassword() throws Exception {
        UUID newUserId = UUID.fromString("10000000-0000-0000-0000-000000000004");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(newUserId);
            return saved;
        });

        mvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"  New customer  ","email":"NEW@example.test",
                                 "password":"Regression-test-password","phone":"0123456789"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(newUserId.toString()))
                .andExpect(jsonPath("$.fullName").value("New customer"))
                .andExpect(jsonPath("$.email").value("new@example.test"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        User registered = savedUser.getValue();
        assertEquals(Set.of("CUSTOMER"), registered.getRoles().stream().map(Role::getName)
                .collect(java.util.stream.Collectors.toSet()));
        assertNotEquals(TEST_PASSWORD, registered.getPassword());
        assertTrue(context.getBean(PasswordEncoder.class).matches(TEST_PASSWORD, registered.getPassword()));
    }

    @ParameterizedTest
    @CsvSource({"CUSTOMER,customer@example.test", "STAFF,staff@example.test", "ADMIN,admin@example.test"})
    void publicLoginReturnsBearerTokenWithExistingClaimsForEachRole(String role, String email)
            throws Exception {
        String response = mvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value(role))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(response, "$.accessToken");
        Claims claims = Jwts.parser().verifyWith(testSigningKey(1)).build()
                .parseSignedClaims(token).getPayload();
        assertEquals(usersByEmail.get(email).getId().toString(), claims.getSubject());
        assertEquals(email, claims.get("email", String.class));
        assertEquals(role, claims.get("roles", String.class));
        assertEquals(Set.of("sub", "email", "roles", "iat", "exp"), claims.keySet());
        assertEquals(EXPIRATION_MS, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void unknownLoginUserReturnsUnauthorizedJson() throws Exception {
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.test\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/users/login"));
    }

    @Test
    void incorrectLoginPasswordReturnsUnauthorizedJson() throws Exception {
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@example.test\",\"password\":\"incorrect-test-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void inactiveLoginUserReturnsForbiddenJson() throws Exception {
        usersById.get(CUSTOMER_ID).setStatus("INACTIVE");
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@example.test\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void invalidRegistrationBodyReturnsBadRequestAndDoesNotSave() throws Exception {
        mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void duplicateRegistrationReturnsConflictAndDoesNotSave() throws Exception {
        when(userRepository.existsByEmailIgnoreCase("customer@example.test")).thenReturn(true);
        mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Duplicate customer\",\"email\":\"customer@example.test\","
                                + "\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        verify(userRepository, never()).save(any(User.class));
    }

    @ParameterizedTest
    @CsvSource({"ANONYMOUS,401", "CUSTOMER,200", "STAFF,403", "ADMIN,403"})
    void cartUsesCustomerOnlyRoleMatcher(String role, int expectedStatus) throws Exception {
        mvc.perform(requestForRole("/api/cart", role)).andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) {
            verify(cartService).getCart(CUSTOMER_ID);
        } else {
            verifyNoInteractions(cartService);
        }
    }

    @ParameterizedTest
    @CsvSource({"ANONYMOUS,401", "CUSTOMER,403", "STAFF,200", "ADMIN,200"})
    void orderManagementAllowsStaffAndAdmin(String role, int expectedStatus) throws Exception {
        mvc.perform(requestForRole("/api/orders/management", role)).andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) {
            verify(orderService).getOrdersForManagement();
        } else {
            verifyNoInteractions(orderService);
        }
    }

    @ParameterizedTest
    @CsvSource({"ANONYMOUS,401", "CUSTOMER,403", "STAFF,403", "ADMIN,200"})
    void staffManagementAllowsOnlyAdmin(String role, int expectedStatus) throws Exception {
        mvc.perform(requestForRole("/api/staff", role)).andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) {
            verify(staffService).getAllStaff();
        } else {
            verifyNoInteractions(staffService);
        }
    }

    @Test
    void missingBearerTokenReturnsUnauthorizedJsonAndChallenge() throws Exception {
        mvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/cart"));
        verifyNoInteractions(userRepository, cartService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPTY", "MALFORMED", "WRONG_KEY", "EXPIRED"})
    void invalidBearerTokenReturnsUnauthorizedJsonBeforeRepositoryLookup(String tokenKind) throws Exception {
        String token = switch (tokenKind) {
            case "EMPTY" -> "";
            case "MALFORMED" -> "invalid-test-token";
            case "WRONG_KEY" -> new JwtService(encodedTestKey(2), EXPIRATION_MS)
                    .generateToken(usersById.get(CUSTOMER_ID));
            case "EXPIRED" -> Jwts.builder().subject(CUSTOMER_ID.toString())
                    .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                    .expiration(Date.from(Instant.now().minusSeconds(60)))
                    .signWith(testSigningKey(1)).compact();
            default -> throw new IllegalArgumentException("Unknown test token kind");
        };

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
        verifyNoInteractions(userRepository, cartService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "MISSING"})
    void validTokenForUnavailableAccountReturnsUnauthorized(String accountState) throws Exception {
        String token = jwtService.generateToken(usersById.get(CUSTOMER_ID));
        if ("MISSING".equals(accountState)) {
            usersById.remove(CUSTOMER_ID);
        } else {
            usersById.get(CUSTOMER_ID).setStatus(accountState);
        }

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verify(userRepository).findById(CUSTOMER_ID);
        verifyNoInteractions(cartService);
    }

    @Test
    void wrongRoleReturnsForbiddenJsonWithoutAuthenticationChallenge() throws Exception {
        mvc.perform(requestForRole("/api/staff", "STAFF"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/staff"));
        verifyNoInteractions(staffService);
    }

    @Test
    void authorizationUsesCurrentRepositoryRolesRatherThanTokenRoleClaims() throws Exception {
        String token = jwtService.generateToken(usersById.get(ADMIN_ID));
        usersById.get(ADMIN_ID).setRoles(Set.of(Role.builder().name("STAFF").build()));

        mvc.perform(get("/api/staff").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/management").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verifyNoInteractions(staffService);
        verify(orderService).getOrdersForManagement();
    }

    @Test
    void repositoryIdentityDeterminesCustomerDataEvenWhenTokenEmailClaimDiffers() throws Exception {
        String token = Jwts.builder().subject(CUSTOMER_ID.toString())
                .claim("email", "admin@example.test").claim("roles", "ADMIN")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(testSigningKey(1)).compact();

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(CUSTOMER_ID.toString()));
        verify(userRepository).findByEmailIgnoreCase("customer@example.test");
        verify(cartService).getCart(CUSTOMER_ID);
    }

    @Test
    void invalidBearerTokenIsRejectedEvenOnPublicRoute() throws Exception {
        mvc.perform(get("/api/test").header("Authorization", "Bearer invalid-test-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(userRepository);
    }

    private void addUser(UUID id, String email, String role) {
        User user = User.builder().id(id).email(email).fullName("Security regression fixture")
                .password(TEST_PASSWORD_HASH).status("ACTIVE")
                .roles(Set.of(Role.builder().name(role).build())).build();
        usersById.put(id, user);
        usersByEmail.put(email, user);
    }

    private MockHttpServletRequestBuilder requestForRole(String path, String role) {
        MockHttpServletRequestBuilder request = get(path);
        if ("ANONYMOUS".equals(role)) {
            return request;
        }
        UUID userId = switch (role) {
            case "CUSTOMER" -> CUSTOMER_ID;
            case "STAFF" -> STAFF_ID;
            case "ADMIN" -> ADMIN_ID;
            default -> throw new IllegalArgumentException("Unknown test fixture role");
        };
        return request.header("Authorization", "Bearer " + jwtService.generateToken(usersById.get(userId)));
    }

    private static String encodedTestKey(int seed) {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + seed);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static SecretKey testSigningKey(int seed) {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(encodedTestKey(seed)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestSecurityExceptionHandler.class,
            JwtService.class, UserService.class, UserController.class, CartController.class,
            OrderController.class, StaffController.class, TestController.class, ApiExceptionHandler.class})
    static class IsolatedSecurityMvcConfiguration {

        // Keep Google token verification and external JWKS access outside this MVC/JWT suite.
        @Bean
        GoogleAuthService googleAuthService() {
            return mock(GoogleAuthService.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        RoleRepository roleRepository() {
            return mock(RoleRepository.class);
        }

        @Bean
        CartService cartService() {
            return mock(CartService.class);
        }

        @Bean
        OrderService orderService() {
            return mock(OrderService.class);
        }

        @Bean
        StaffService staffService() {
            return mock(StaffService.class);
        }
    }
}
