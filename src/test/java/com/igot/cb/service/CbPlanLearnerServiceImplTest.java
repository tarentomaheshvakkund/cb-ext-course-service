package com.igot.cb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.cache.CbPlanCacheMgr;
import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.util.AccessTokenValidator;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CbPlanLearnerServiceImplTest {

    @Mock(lenient = true)
    private AccessTokenValidator accessTokenValidator;

    @Mock(lenient = true)
    private CassandraOperation cassandraOperation;

    @Mock(lenient = true)
    private ContentInfoServiceImpl contentService;

    @Mock(lenient = true)
    private CbPlanCacheMgr cbPlanCacheMgr;


    private CbPlanLearnerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CbPlanLearnerServiceImpl(accessTokenValidator, cassandraOperation, cbPlanCacheMgr);
        ReflectionTestUtils.setField(service, "contentService", contentService);
    }

    @Test
    void testGetCBPlanListForUser_Success() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");

        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(Arrays.asList(userData));

        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_CB_PLAN_V2_LOOKUP_BY_ALL_ORG), any(), any(), any()))
                .thenReturn(new ArrayList<>());

        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_CB_PLAN_V2_LOOKUP_BY_ORG), any(), any(), any()))
                .thenReturn(new ArrayList<>());

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    @Test
    void testGetCBPlanListForUser_UserNotFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(new ArrayList<>());

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testGetCBPlanListForUser_WithActivePlans() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");

        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(Arrays.asList(userData));

        // Prepare a plan that would be returned from cache
        Map<String, Object> activePlan = new HashMap<>();
        activePlan.put(Constants.PLAN_ID, "plan1");
        activePlan.put(Constants.STATUS, Constants.LIVE);
        activePlan.put(Constants.CONTENT_LIST, Arrays.asList("course1"));
        activePlan.put(Constants.END_DATE_REQUEST, Instant.now());

        // Stub cache manager, since service uses it
        when(cbPlanCacheMgr.getCbPlanForAllAndOrgId("org123"))
                .thenReturn(Arrays.asList(activePlan));

        Map<String, Object> contentDetails = new HashMap<>();
        contentDetails.put(Constants.IDENTIFIER, "course1");
        when(contentService.readContent("course1", null)).thenReturn(contentDetails);

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals(1, response.getResult().get(Constants.COUNT));
    }


    @Test
    void testGetCBPlanListForUser_PrivateMode() {
        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(Arrays.asList(userData));

        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_CB_PLAN_V2_LOOKUP_BY_ALL_ORG), any(), any(), any()))
                .thenReturn(new ArrayList<>());
        
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_CB_PLAN_V2_LOOKUP_BY_ORG), any(), any(), any()))
                .thenReturn(new ArrayList<>());

        ApiResponse response = service.getCBPlanListForUser("org123", "user123", true);

        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        verify(accessTokenValidator, never()).fetchUserIdFromAccessToken(anyString(), any());
    }

    @Test
    void testGetCBPlanListForUser_BlankUserId() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("");

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        assertNotNull(response);
    }

    @Test
    void testGetCBPlanListForUser_Exception() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenThrow(new RuntimeException("Test exception"));

        ApiResponse response = service.getCBPlanListForUser("org123", "token123", false);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testRemoveDuplicateCourses_WithLanguageMap() {
        List<Map<String, Object>> courseList = new ArrayList<>();
        
        Map<String, Object> course1 = new HashMap<>();
        course1.put(Constants.IDENTIFIER, "course1");
        Map<String, Object> langMap1 = new HashMap<>();
        Map<String, Object> langDetails = new HashMap<>();
        langDetails.put(Constants.ID, "lang1");
        langMap1.put("en", langDetails);
        course1.put(Constants.LANGUAGE_MAP_V1, langMap1);
        
        Map<String, Object> course2 = new HashMap<>();
        course2.put(Constants.IDENTIFIER, "course2");
        course2.put(Constants.LANGUAGE_MAP_V1, new HashMap<>());
        
        courseList.add(course1);
        courseList.add(course2);

        List<Map<String, Object>> result = service.removeDuplicateCourses(courseList);

        assertEquals(2, result.size());
    }

    @Test
    void testRemoveDuplicateCourses_EmptyList() {
        List<Map<String, Object>> courseList = new ArrayList<>();
        List<Map<String, Object>> result = service.removeDuplicateCourses(courseList);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseContextData_ValidJson() throws Exception {
        String jsonData = "{\"accessControl\":{\"userGroups\":[]}}";
        
        Method parseMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("parseContextData", Object.class);
        parseMethod.setAccessible(true);
        
        Map<String, Object> result = (Map<String, Object>) parseMethod.invoke(service, jsonData);
        
        assertNotNull(result);
        assertTrue(result.containsKey("accessControl"));
    }

    @Test
    void testParseContextData_InvalidJson() throws Exception {
        String invalidJson = "invalid json";
        
        Method parseMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("parseContextData", Object.class);
        parseMethod.setAccessible(true);
        
        Map<String, Object> result = (Map<String, Object>) parseMethod.invoke(service, invalidJson);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseContextData_NonStringInput() throws Exception {
        Integer nonStringInput = 123;
        
        Method parseMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("parseContextData", Object.class);
        parseMethod.setAccessible(true);
        
        Map<String, Object> result = (Map<String, Object>) parseMethod.invoke(service, nonStringInput);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testSetUserProfile_ValidData() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> userBasicProfile = createUserData();
        
        Method setUserProfileMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        setUserProfileMethod.setAccessible(true);
        
        setUserProfileMethod.invoke(service, userProfile, userBasicProfile);
        
        assertEquals("user123", userProfile.get(Constants.USER));
        assertEquals("org123", userProfile.get(Constants.ROOT_ORG_ID));
    }

    @Test
    void testSetUserProfile_EmptyProfile() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> emptyProfile = new HashMap<>();
        
        Method setUserProfileMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        setUserProfileMethod.setAccessible(true);
        
        setUserProfileMethod.invoke(service, userProfile, emptyProfile);
        
        assertTrue(userProfile.isEmpty());
    }

    @Test
    void testEvaluateContextAccessRule_ValidAccess() throws Exception {
        Map<String, Object> accessSettingMap = createAccessSettingMap();
        Map<String, String> userProfile = createUserProfile();
        
        Method evaluateMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        evaluateMethod.setAccessible(true);
        
        boolean result = (boolean) evaluateMethod.invoke(service, accessSettingMap, userProfile);
        
        assertTrue(result);
    }

    @Test
    void testEvaluateContextAccessRule_NoAccess() throws Exception {
        Map<String, Object> accessSettingMap = createAccessSettingMapNoAccess();
        Map<String, String> userProfile = createUserProfile();
        
        Method evaluateMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        evaluateMethod.setAccessible(true);
        
        boolean result = (boolean) evaluateMethod.invoke(service, accessSettingMap, userProfile);
        
        assertFalse(result);
    }

    @Test
    void testEvaluateContextAccessRule_EmptyMaps() throws Exception {
        Method evaluateMethod = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        evaluateMethod.setAccessible(true);
        
        boolean result = (boolean) evaluateMethod.invoke(service, new HashMap<>(), new HashMap<>());
        
        assertFalse(result);
    }

    private Map<String, Object> createUserData() {
        Map<String, Object> userData = new HashMap<>();
        userData.put(Constants.ID, "user123");
        userData.put("rootorgid", "org123");
        userData.put("profiledetails", "{\"professionalDetails\":[{\"designation\":\"Test\",\"group\":\"TestGroup\"}],\"profileStatus\":\"VERIFIED\",\"cadreDetails\":{\"cadreName\":\"TestCadre\",\"civilServiceName\":\"TestService\",\"cadreBatch\":2020}}");
        return userData;
    }

    private Map<String, String> createUserProfile() {
        Map<String, String> userProfile = new HashMap<>();
        userProfile.put("designation", "Test");
        userProfile.put("group", "TestGroup");
        return userProfile;
    }

    private Map<String, Object> createAccessSettingMap() {
        Map<String, Object> accessControl = new HashMap<>();
        List<Map<String, Object>> userGroups = new ArrayList<>();
        
        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put(Constants.USER_GROUP_NAME, "TestGroup");
        
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        Map<String, Object> criteria = new HashMap<>();
        criteria.put(Constants.CRITERIA_KEY, "designation");
        criteria.put(Constants.CRITERIA_VALUE, Arrays.asList("Test"));
        criteriaList.add(criteria);
        
        userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
        userGroups.add(userGroup);
        
        accessControl.put(Constants.USER_GROUPS, userGroups);
        
        Map<String, Object> accessSettingMap = new HashMap<>();
        accessSettingMap.put(Constants.ACCESS_CONTROL, accessControl);
        
        return accessSettingMap;
    }

    private Map<String, Object> createAccessSettingMapNoAccess() {
        Map<String, Object> accessControl = new HashMap<>();
        List<Map<String, Object>> userGroups = new ArrayList<>();
        
        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put(Constants.USER_GROUP_NAME, "TestGroup");
        
        List<Map<String, Object>> criteriaList = new ArrayList<>();
        Map<String, Object> criteria = new HashMap<>();
        criteria.put(Constants.CRITERIA_KEY, "designation");
        criteria.put(Constants.CRITERIA_VALUE, Arrays.asList("NoMatch"));
        criteriaList.add(criteria);
        
        userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, criteriaList);
        userGroups.add(userGroup);
        
        accessControl.put(Constants.USER_GROUPS, userGroups);
        
        Map<String, Object> accessSettingMap = new HashMap<>();
        accessSettingMap.put(Constants.ACCESS_CONTROL, accessControl);
        
        return accessSettingMap;
    }
    @Test
    void testGetExistingContextData_WithCustomFields() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> userBasicProfile = createUserData();

        // Prepare custom field TEXT type
        Map<String, Object> customFieldText = new HashMap<>();
        customFieldText.put(Constants.TYPE, Constants.TEXT);
        customFieldText.put(Constants.ATTRIBUTE_NAME, "customText");
        customFieldText.put(Constants.VALUE, "CustomValue");

        // Prepare custom field MASTER_LIST type
        Map<String, Object> masterListValue = new HashMap<>();
        masterListValue.put(Constants.ATTRIBUTE_NAME, "skill");
        masterListValue.put(Constants.VALUE, "Java");

        Map<String, Object> customFieldMasterList = new HashMap<>();
        customFieldMasterList.put(Constants.TYPE, Constants.MASTER_LIST);
        customFieldMasterList.put(Constants.VALUES, List.of(masterListValue));

        Map<String, Object> orgAdditionalProperty = new HashMap<>();
        orgAdditionalProperty.put(Constants.ORGANISATION_ID, "org123");
        orgAdditionalProperty.put(Constants.CUSTOM_FIELD_VALUES, List.of(customFieldText, customFieldMasterList));

        String json = new ObjectMapper().writeValueAsString(List.of(orgAdditionalProperty));
        Map<String, Object> row = Map.of(Constants.CONTEXT_DATA_KEY, json);

        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER_EXTENDED_PROFILE), any(), any(), any()))
                .thenReturn(List.of(row));

        // Call private method via reflection
        Method method = CbPlanLearnerServiceImpl.class.getDeclaredMethod("getExistingContextData", String.class, String.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, "user123", "org123", userProfile);

        assertEquals("CustomValue", userProfile.get("customText"));
        assertEquals("Java", userProfile.get("skill"));
    }

    @Test
    void testGetCBPlanListForUser_ParseContextDataException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(List.of(userData));
        Map<String, Object> plan = new HashMap<>();
        plan.put(Constants.PLAN_ID, "p1");
        plan.put(Constants.STATUS, Constants.LIVE);
        plan.put(Constants.CONTENT_LIST, List.of("c1"));
        plan.put(Constants.CONTEXT_DATA_REQUEST, "{badJson}");
        when(cbPlanCacheMgr.getCbPlanForAllAndOrgId("org123"))
                .thenReturn(List.of(plan));
        ApiResponse resp = service.getCBPlanListForUser("org123", "token", false);
        assertEquals(Constants.SUCCESS, resp.getParams().getStatus());
    }

    @Test
    void testGetCBPlanListForUser_CourseWithRc_NoSecureSettings() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(List.of(userData));
        Map<String, Object> plan = new HashMap<>();
        plan.put(Constants.PLAN_ID, "p1");
        plan.put(Constants.STATUS, Constants.LIVE);
        plan.put(Constants.CONTENT_LIST, List.of("course_rc"));
        plan.put(Constants.END_DATE_REQUEST, new Date());
        when(cbPlanCacheMgr.getCbPlanForAllAndOrgId("org123"))
                .thenReturn(List.of(plan));
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.IDENTIFIER, "course_rc");
        content.put(Constants.SECURE_SETTINGS, Collections.emptyMap());
        when(contentService.readContent("course_rc", null)).thenReturn(content);
        ApiResponse resp = service.getCBPlanListForUser("org123", "token", false);
        assertNotNull(resp);
    }

    @Test
    void testGetCBPlanListForUser_ContentServiceReturnsNull() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(List.of(userData));
        Map<String, Object> plan = new HashMap<>();
        plan.put(Constants.PLAN_ID, "p1");
        plan.put(Constants.STATUS, Constants.LIVE);
        plan.put(Constants.CONTENT_LIST, List.of("cX"));
        when(cbPlanCacheMgr.getCbPlanForAllAndOrgId("org123"))
                .thenReturn(List.of(plan));
        when(contentService.readContent("cX", null)).thenReturn(Collections.emptyMap());
        ApiResponse resp = service.getCBPlanListForUser("org123", "token", false);
        assertNotNull(resp);
    }

    @Test
    void testSetUserProfile_RawValueAsMap() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> profileMap = new HashMap<>();
        profileMap.put(Constants.ID, "u1");
        profileMap.put("rootorgid", "org1");
        profileMap.put("profiledetails", Map.of(
                Constants.PROFESSIONAL_DETAILS, List.of(Map.of(Constants.DESIGNATION, "Dev", Constants.GROUP, "Grp")),
                Constants.PROFILE_STATUS_KEY, "VERIFIED",
                Constants.CADRE_DETAILS, Map.of(Constants.CADRE_NAME, "Cadre", Constants.CIVIL_SERVICE_NAME, "Service")
        ));
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, userProfile, profileMap);
        assertEquals("Dev", userProfile.get(Constants.DESIGNATION));
        assertEquals("Cadre", userProfile.get(Constants.CADRE));
    }

    @Test
    void testEvaluateContextAccessRule_NoAccessControl() throws Exception {
        Map<String, Object> settings = new HashMap<>();
        Map<String, String> profile = Map.of(Constants.DESIGNATION, "Test");
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(service, settings, profile);
        assertFalse(result);
    }

    @Test
    void testEvaluateContextAccessRule_CentralDeputation() throws Exception {
        Map<String, Object> criteria = new HashMap<>();
        criteria.put(Constants.CRITERIA_KEY, Constants.CENTRAL_DEPUTATION);
        criteria.put(Constants.CRITERIA_VALUE, true);
        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, List.of(criteria));
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(userGroup));
        Map<String, Object> settings = Map.of(Constants.ACCESS_CONTROL, accessControl);
        Map<String, String> profile = new HashMap<>();
        profile.put(Constants.CENTRAL_DEPUTATION, "true");
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(service, settings, profile);
        assertTrue(result);
    }

    @Test
    void testGetExistingContextData_ParseException() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        String badJson = "{invalid";
        Map<String, Object> row = Map.of(Constants.CONTEXT_DATA_KEY, badJson);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(row));
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("getExistingContextData", String.class, String.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, "u1", "org1", userProfile);
        assertTrue(userProfile.isEmpty());
    }

    @Test
    void testGetExistingContextData_OrgIdNotMatch() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> orgProp = new HashMap<>();
        orgProp.put(Constants.ORGANISATION_ID, "differentOrg");
        orgProp.put(Constants.CUSTOM_FIELD_VALUES, List.of());
        String json = new ObjectMapper().writeValueAsString(List.of(orgProp));
        Map<String, Object> row = Map.of(Constants.CONTEXT_DATA_KEY, json);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(row));
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("getExistingContextData", String.class, String.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, "u1", "org1", userProfile);
        assertTrue(userProfile.isEmpty());
    }

    @Test
    void testGetCBPlanListForUser_UserIdWhitespace() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("   ");
        ApiResponse resp = service.getCBPlanListForUser("org123", "token", false);
        assertNotNull(resp);
        assertEquals(Constants.SUCCESS, resp.getParams().getStatus()); // returns default with blank userId
    }

    @Test
    void testEvaluateContextAccessRule_EmptyCriteriaList() throws Exception {
        Map<String, Object> userGroup = new HashMap<>();
        userGroup.put(Constants.USER_GROUP_NAME, "grp");
        userGroup.put(Constants.USER_GROUP_CRITERIA_LIST, Collections.emptyList());
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(userGroup));
        Map<String, Object> settings = Map.of(Constants.ACCESS_CONTROL, accessControl);
        Map<String, String> profile = createUserProfile();
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(service, settings, profile);
        assertFalse(result);
    }

    @Test
    void testEvaluateContextAccessRule_UserProfileMissingKey() throws Exception {
        Map<String, Object> criteria = Map.of(Constants.CRITERIA_KEY, "designation", Constants.CRITERIA_VALUE, List.of("X"));
        Map<String, Object> userGroup = Map.of(Constants.USER_GROUP_NAME, "grp", Constants.USER_GROUP_CRITERIA_LIST, List.of(criteria));
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(userGroup));
        Map<String, Object> settings = Map.of(Constants.ACCESS_CONTROL, accessControl);

        Map<String, String> profile = new HashMap<>(); // missing designation

        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(service, settings, profile);
        assertFalse(result);
    }

    @Test
    void testGetExistingContextData_CustomFieldValuesEmpty() throws Exception {
        Map<String, Object> orgProp = new HashMap<>();
        orgProp.put(Constants.ORGANISATION_ID, "org123");
        orgProp.put(Constants.CUSTOM_FIELD_VALUES, Collections.emptyList());
        String json = new ObjectMapper().writeValueAsString(List.of(orgProp));
        Map<String, Object> row = Map.of(Constants.CONTEXT_DATA_KEY, json);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(row));
        Map<String, String> userProfile = new HashMap<>();
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("getExistingContextData", String.class, String.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, "u1", "org123", userProfile);
        assertTrue(userProfile.isEmpty());
    }

    @Test
    void testGetExistingContextData_MasterListEmptyValues() throws Exception {
        Map<String, Object> custom = new HashMap<>();
        custom.put(Constants.TYPE, Constants.MASTER_LIST);
        custom.put(Constants.VALUES, Collections.emptyList());
        Map<String, Object> orgProp = new HashMap<>();
        orgProp.put(Constants.ORGANISATION_ID, "org123");
        orgProp.put(Constants.CUSTOM_FIELD_VALUES, List.of(custom));
        String json = new ObjectMapper().writeValueAsString(List.of(orgProp));
        Map<String, Object> row = Map.of(Constants.CONTEXT_DATA_KEY, json);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(row));
        Map<String, String> userProfile = new HashMap<>();
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("getExistingContextData", String.class, String.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, "u1", "org123", userProfile);
        assertTrue(userProfile.isEmpty());
    }

    @Test
    void testGetCBPlanListForUser_ActiveCbPlansNull() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(List.of(userData));
        when(cbPlanCacheMgr.getCbPlanForAllAndOrgId("org123")).thenReturn(null);
        ApiResponse resp = service.getCBPlanListForUser("org123", "token", false);
        assertEquals(0, resp.getResult().get(Constants.COUNT));
    }

    @Test
    void testGetCBPlanListForUser_DuplicateCourseIdSkipped() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any())).thenReturn("user123");
        Map<String, Object> userData = createUserData();
        when(cassandraOperation.getRecordsByProperties(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.USER), any(), any(), any()))
                .thenReturn(List.of(userData));
        Map<String, Object> plan = new HashMap<>();
        plan.put(Constants.PLAN_ID, "p1");
        plan.put(Constants.CONTENT_LIST, List.of("c1", "c1")); // duplicate course id
        when(cbPlanCacheMgr.getCbPlanForAllAndOrgId("org123")).thenReturn(List.of(plan));
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.IDENTIFIER, "c1");
        when(contentService.readContent("c1", null)).thenReturn(content);
        ApiResponse resp = service.getCBPlanListForUser("org123", "token", false);
        assertEquals(1, resp.getResult().get(Constants.COUNT));
    }

    @Test
    void testRemoveDuplicateCourses_DuplicateIdentifiers() {
        Map<String, Object> c1 = new HashMap<>();
        c1.put(Constants.IDENTIFIER, "same");
        c1.put(Constants.LANGUAGE_MAP_V1, Map.of("en", Map.of(Constants.ID, "same")));
        Map<String, Object> c2 = new HashMap<>();
        c2.put(Constants.IDENTIFIER, "same");
        c2.put(Constants.LANGUAGE_MAP_V1, Map.of());
        List<Map<String, Object>> list = List.of(c1, c2);
        List<Map<String, Object>> result = service.removeDuplicateCourses(list);
        assertEquals(1, result.size()); // second skipped
    }

    @Test
    void testRemoveDuplicateCourses_NullLanguageId() {
        Map<String, Object> c1 = new HashMap<>();
        c1.put(Constants.IDENTIFIER, "c1");
        c1.put(Constants.LANGUAGE_MAP_V1, Map.of("en", Map.of()));
        List<Map<String, Object>> result = service.removeDuplicateCourses(List.of(c1));
        assertEquals(1, result.size());
    }

    @Test
    void testSetUserProfile_CadreCentralDeputationTrue() throws Exception {
        Map<String, String> userProfile = new HashMap<>();
        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.ID, "u1");
        profile.put("rootorgid", "org1");
        profile.put("profiledetails", Map.of(
                Constants.CADRE_DETAILS, Map.of(
                        Constants.CADRE_NAME, "Cadre",
                        Constants.CIVIL_SERVICE_NAME, "Service",
                        Constants.CENTRAL_DEPUTATION, true
                )
        ));
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("setUserProfile", Map.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, userProfile, profile);
        assertEquals("true", userProfile.get(Constants.CENTRAL_DEPUTATION));
    }

    @Test
    void testEvaluateContextAccessRule_UserGroupsNull() throws Exception {
        Map<String, Object> accessControl = new HashMap<>();
        accessControl.put(Constants.USER_GROUPS, null);
        Map<String, Object> settings = Map.of(Constants.ACCESS_CONTROL, accessControl);
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(service, settings, Map.of(Constants.DESIGNATION, "Test"));
        assertFalse(result);
    }

    @Test
    void testEvaluateContextAccessRule_CriteriaValueAsString() throws Exception {
        Map<String, Object> criteria = Map.of(Constants.CRITERIA_KEY, "designation", Constants.CRITERIA_VALUE, "Test");
        Map<String, Object> userGroup = Map.of(Constants.USER_GROUP_NAME, "grp", Constants.USER_GROUP_CRITERIA_LIST, List.of(criteria));
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(userGroup));
        Map<String, Object> settings = Map.of(Constants.ACCESS_CONTROL, accessControl);
        Map<String, String> profile = Map.of("designation", "Test");
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(service, settings, profile);
        assertTrue(result);
    }

    @Test
    void testEvaluateContextAccessRule_UserFailsCriteria() throws Exception {
        Map<String, Object> crit = Map.of(Constants.CRITERIA_KEY, "designation", Constants.CRITERIA_VALUE, List.of("X"));
        Map<String, Object> userGroup = Map.of(Constants.USER_GROUP_NAME, "g", Constants.USER_GROUP_CRITERIA_LIST, List.of(crit));
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(userGroup));
        Map<String, Object> settings = Map.of(Constants.ACCESS_CONTROL, accessControl);
        Map<String, String> profile = Map.of("designation", "Y");
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(service, settings, profile);
        assertFalse(result);
    }

    @Test
    void testEvaluateContextAccessRule_PartialCriteriaMatchFails() throws Exception {
        Map<String, Object> c1 = Map.of(Constants.CRITERIA_KEY, "designation", Constants.CRITERIA_VALUE, List.of("Dev"));
        Map<String, Object> c2 = Map.of(Constants.CRITERIA_KEY, "group", Constants.CRITERIA_VALUE, List.of("OtherGroup"));
        Map<String, Object> userGroup = Map.of(Constants.USER_GROUP_NAME, "grp", Constants.USER_GROUP_CRITERIA_LIST, List.of(c1, c2));
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, List.of(userGroup));
        Map<String, Object> settings = Map.of(Constants.ACCESS_CONTROL, accessControl);
        Map<String, String> profile = Map.of("designation", "Dev", "group", "Mismatch");
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(service, settings, profile);
        assertFalse(result);
    }

    @Test
    void testGetExistingContextData_NoRows() throws Exception {
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(Collections.emptyList());
        Map<String, String> profile = new HashMap<>();
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("getExistingContextData", String.class, String.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, "u1", "org1", profile);
        assertTrue(profile.isEmpty());
    }

    @Test
    void testGetExistingContextData_UnsupportedCustomFieldType() throws Exception {
        Map<String, Object> custom = new HashMap<>();
        custom.put(Constants.TYPE, "UNKNOWN");
        custom.put(Constants.ATTRIBUTE_NAME, "attr");
        custom.put(Constants.VALUE, "val");
        Map<String, Object> orgProp = new HashMap<>();
        orgProp.put(Constants.ORGANISATION_ID, "org123");
        orgProp.put(Constants.CUSTOM_FIELD_VALUES, List.of(custom));
        String json = new ObjectMapper().writeValueAsString(List.of(orgProp));
        Map<String, Object> row = Map.of(Constants.CONTEXT_DATA_KEY, json);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(row));
        Map<String, String> profile = new HashMap<>();
        Method m = CbPlanLearnerServiceImpl.class.getDeclaredMethod("getExistingContextData", String.class, String.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, "u1", "org123", profile);
        assertTrue(profile.isEmpty());
    }

    @Test
    void testEvaluateContextAccessRule_AccessControlEmpty() throws Exception {
        Map<String, Object> settings = Map.of(Constants.ACCESS_CONTROL, Collections.emptyMap());
        Map<String, String> profile = Map.of(Constants.DESIGNATION, "Dev");
        Method m = CbPlanLearnerServiceImpl.class
                .getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(service, settings, profile);
        assertFalse(result);
    }

    @Test
    void testEvaluateContextAccessRule_UserGroupsEmptyList() throws Exception {
        Map<String, Object> accessControl = Map.of(Constants.USER_GROUPS, Collections.emptyList());
        Map<String, Object> settings = Map.of(Constants.ACCESS_CONTROL, accessControl);
        Method m = CbPlanLearnerServiceImpl.class
                .getDeclaredMethod("evaluateContextAccessRule", Map.class, Map.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(service, settings, new HashMap<>());
        assertFalse(result);
    }

    @Test
    void testGetExistingContextData_RowsNull() throws Exception {
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(null);
        Map<String, String> profile = new HashMap<>();
        Method m = CbPlanLearnerServiceImpl.class
                .getDeclaredMethod("getExistingContextData", String.class, String.class, Map.class);
        m.setAccessible(true);
        m.invoke(service, "u1", "org1", profile);
        assertTrue(profile.isEmpty());
    }


}