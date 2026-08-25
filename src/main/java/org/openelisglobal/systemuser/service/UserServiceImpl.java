package org.openelisglobal.systemuser.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.services.DisplayListService.ListType;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.login.service.LoginUserService;
import org.openelisglobal.login.valueholder.LoginUser;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.program.service.ProgramService;
import org.openelisglobal.resultvalidation.bean.AnalysisItem;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.security.SamlRoleMapping;
import org.openelisglobal.security.SamlRoleMapping.ParsedSamlRole;
import org.openelisglobal.security.service.UserPermissionService;
import org.openelisglobal.systemuser.controller.UnifiedSystemUserController;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.openelisglobal.test.service.TestSectionService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.test.valueholder.TestSection;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSample;
import org.openelisglobal.userrole.service.UserRoleService;
import org.openelisglobal.userrole.valueholder.LabUnitRoleMap;
import org.openelisglobal.userrole.valueholder.UserLabUnitRoles;
import org.openelisglobal.userrole.valueholder.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class UserServiceImpl implements UserService {

    private static final Map<String, List<String>> LAB_ROLE_GROUPS = Map.of(
            Constants.ROLE_GENERIC_SAMPLE, List.of(Constants.ROLE_SAMPLE_MANAGEMENT),
            Constants.ROLE_ORDER, List.of(Constants.ROLE_ORDER_ADD, Constants.ROLE_ORDER_EDIT),
            Constants.ROLE_PATIENT, List.of(Constants.ROLE_PATIENT_MANAGEMENT, Constants.ROLE_PATIENT_HISTORY),
            Constants.ROLE_STORAGE, List.of(Constants.ROLE_STORAGE_MANAGEMENT),
            Constants.ROLE_RESULTS,
            List.of(Constants.ROLE_RESULTS_BY_UNIT, Constants.ROLE_RESULTS_BY_PATIENT,
                    Constants.ROLE_RESULTS_BY_ORDER),
            Constants.ROLE_VALIDATION, List.of(Constants.ROLE_VALIDATION_ROUTINE, Constants.ROLE_VALIDATION_BY_ORDER));

    @Autowired
    private LoginUserService loginService;
    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private TypeOfSampleService typeOfSampleService;
    @Autowired
    private ProgramService programService;
    @Autowired
    private TestService testService;
    @Autowired
    private TestSectionService testSectionService;
    @Autowired
    private HttpSession session;
    @Autowired
    private UserPermissionService userPermissionService;
    @Value("${org.itech.login.saml.legacyRoleFallback:false}")
    private boolean samlLegacyRoleFallback;

    @Override
    @Transactional
    public void updateLoginUser(LoginUser loginUser, boolean loginUserNew, SystemUser systemUser, boolean systemUserNew,
            List<String> selectedRoles, String loggedOnUserId) {
        if (loginUserNew) {
            loginService.insert(loginUser);
        } else {
            loginService.update(loginUser);
        }

        if (systemUserNew) {
            systemUserService.insert(systemUser);
        } else {
            systemUserService.update(systemUser);
        }

        updateUserRoles(selectedRoles, systemUser, loggedOnUserId, false);
    }

    @Override
    @Transactional
    public void saveUserLabUnitRoles(SystemUser systemUser, Map<String, Set<String>> selectedLabUnitRolesMap,
            String loggedOnUserId) {
        if (selectedLabUnitRolesMap == null) {
            selectedLabUnitRolesMap = Map.of();
        }
        UserLabUnitRoles userLabUnitRoles = userRoleService.getUserLabUnitRoles(systemUser.getId());
        Set<LabUnitRoleMap> labUnitRoleMaps;
        if (userLabUnitRoles == null) {
            userLabUnitRoles = new UserLabUnitRoles();
            userLabUnitRoles.setId(Integer.valueOf(systemUser.getId()));
            labUnitRoleMaps = new HashSet<>();
        } else {
            labUnitRoleMaps = userLabUnitRoles.getLabUnitRoleMap();
            for (LabUnitRoleMap roleMap : labUnitRoleMaps) {
                userRoleService.deleteLabUnitRoleMap(roleMap);
            }
            labUnitRoleMaps.clear();
        }
        Set<String> labUnitRoles = new HashSet<>();
        for (String labUnit : selectedLabUnitRolesMap.keySet()) {
            if (StringUtils.isNotEmpty(labUnit)) {
                Set<String> normalizedLabRoles = normalizeGroupedLabRoleIds(selectedLabUnitRolesMap.get(labUnit));
                LabUnitRoleMap labUnitRoleMap = new LabUnitRoleMap();
                labUnitRoleMap.setLabUnit(labUnit);
                labUnitRoleMap.setRoles(normalizedLabRoles);
                labUnitRoleMaps.add(labUnitRoleMap);
                for (String role : normalizedLabRoles) {
                    labUnitRoles.add(role);
                }
            }
        }
        userLabUnitRoles.setLabUnitRoleMap(labUnitRoleMaps);
        userRoleService.saveOrUpdateUserLabUnitRoles(userLabUnitRoles);
        updateUserRoles(labUnitRoles.stream().collect(Collectors.toList()), systemUser, loggedOnUserId, true);
    }

    @Override
    @Transactional
    public UserLabUnitRoles getUserLabUnitRoles(String systemUserId) {
        return userRoleService.getUserLabUnitRoles(systemUserId);
    }

    @Override
    @Transactional
    public List<UserLabUnitRoles> getAllUserLabUnitRoles() {
        return userRoleService.getAllUserLabUnitRoles();
    }

    private void updateUserRoles(List<String> selectedRoles, SystemUser systemUser, String loggedOnUserId,
            Boolean isLabRole) {
        if (selectedRoles == null) {
            selectedRoles = new ArrayList<>();
        }
        List<String> currentUserRoles = userRoleService.getRoleIdsForUser(systemUser.getId());
        List<UserRole> deletedUserRoles = new ArrayList<>();
        if (isLabRole) {
            List<String> rolesToPreserve = currentUserRoles.stream()
                    .filter(roleId -> !isLabPermissionRole(roleId))
                    .collect(Collectors.toList());
            selectedRoles.addAll(rolesToPreserve);
        }

        for (int i = 0; i < selectedRoles.size(); i++) {
            if (!currentUserRoles.contains(selectedRoles.get(i))) {
                UserRole userRole = new UserRole();
                userRole.setSystemUserId(systemUser.getId());
                userRole.setRoleId(selectedRoles.get(i));
                userRole.setSysUserId(loggedOnUserId);
                userRoleService.insert(userRole);
            } else {
                currentUserRoles.remove(selectedRoles.get(i));
            }
        }

        for (String roleId : currentUserRoles) {
            UserRole userRole = new UserRole();
            userRole.setSystemUserId(systemUser.getId());
            userRole.setRoleId(roleId);
            userRole.setSysUserId(loggedOnUserId);
            deletedUserRoles.add(userRole);
        }

        if (deletedUserRoles.size() > 0) {
            userRoleService.deleteAll(deletedUserRoles);
        }
    }

    private boolean isLabPermissionRole(String roleId) {
        if (StringUtils.isBlank(roleId)) {
            return false;
        }

        Role role = roleService.getRoleById(roleId);
        if (role == null || StringUtils.isBlank(role.getGroupingParent())) {
            return false;
        }

        Role labRolesGroup = roleService.getRoleByName(Constants.LAB_ROLES_GROUP);
        if (labRolesGroup == null || StringUtils.isBlank(labRolesGroup.getId())) {
            return false;
        }

        return labRolesGroup.getId().equals(role.getGroupingParent());
    }

    private Set<String> normalizeGroupedLabRoleIds(Set<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<String> normalizedIds = new LinkedHashSet<>(roleIds.stream().filter(StringUtils::isNotBlank)
                .map(StringUtils::trim).collect(Collectors.toCollection(LinkedHashSet::new)));

        Map<String, String> roleIdByName = normalizedIds.stream().map(roleService::getRoleById).filter(Objects::nonNull)
                .filter(role -> StringUtils.isNotBlank(role.getName()) && StringUtils.isNotBlank(role.getId()))
                .collect(Collectors.toMap(role -> StringUtils.trim(role.getName()), Role::getId, (left, right) -> left,
                        LinkedHashMap::new));

        LAB_ROLE_GROUPS.forEach((parentRoleName, childRoleNames) -> {
            boolean hasSelectedChild = childRoleNames.stream().map(roleIdByName::get).filter(Objects::nonNull)
                    .anyMatch(normalizedIds::contains);
            if (hasSelectedChild) {
                String parentRoleId = roleIdByName.get(parentRoleName);
                if (StringUtils.isNotBlank(parentRoleId)) {
                    normalizedIds.remove(parentRoleId);
                }
            }
        });

        return normalizedIds;
    }

    @Override
    public List<IdValuePair> getUserTestSections(String systemUserId, String roleId) {
        Authentication authentication = null;
        // see filter org.openelisglobal.security.AjaxFilter to handle
        // RequestContextHolder for Ajax calls via servlets
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        HttpServletRequest request = null;
        if (requestAttributes instanceof ServletRequestAttributes) {
            request = ((ServletRequestAttributes) requestAttributes).getRequest();

            HttpSession requestSession = request.getSession(false);
            if (requestSession == null) {
                authentication = null;
            } else {
                Object sc = requestSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                if (!(sc instanceof SecurityContext)) {
                    LogEvent.logWarn(this.getClass().getSimpleName(), "getUserLogin",
                            "security context is not of type SecurityContext");
                } else {
                    authentication = ((SecurityContext) sc).getAuthentication();
                }
            }
        } else {
            LogEvent.logWarn(this.getClass().getSimpleName(), "getUserLogin",
                    "requestAttributes is not of type ServletRequestAttributes");
        }
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                List<IdValuePair> userTestSections = new ArrayList<>();
                Boolean requireLabUnitAtLogin = ConfigurationProperties.getInstance()
                        .getPropertyValue(Property.REQUIRE_LAB_UNIT_AT_LOGIN).equals("true");
                UserSessionData usd = (UserSessionData) session.getAttribute("userSessionData");
                boolean hasGlobalTestSectionAccess = hasGlobalTestSectionAccess(systemUserId);
                TestSection logintestSection = null;
                if (requireLabUnitAtLogin && !hasGlobalTestSectionAccess) {
                    if (usd.getLoginLabUnit() != 0) {
                        logintestSection = testSectionService.getTestSectionById(String.valueOf(usd.getLoginLabUnit()));
                        if (logintestSection != null) {
                            userTestSections.add(
                                    new IdValuePair(logintestSection.getId(), logintestSection.getLocalizedName()));
                            return userTestSections;
                        }
                    }

                }

                List<String> userLabUnits = new ArrayList<>();
                UserLabUnitRoles userLabRoles = getUserLabUnitRoles(systemUserId);
                if (userLabRoles != null) {
                    userLabRoles.getLabUnitRoleMap().forEach(roles -> {
                        if (roleId == null) {
                            userLabUnits.add(roles.getLabUnit());
                        } else {
                            org.openelisglobal.common.log.LogEvent.logInfo(this.getClass().getSimpleName(),
                                    "getUserTestSections", "Checking labUnit=" + roles.getLabUnit() + ", roles="
                                            + roles.getRoles() + ", roleId=" + roleId);
                            if (roles.getRoles().contains(roleId)) {
                                userLabUnits.add(roles.getLabUnit());
                            }
                        }

                    });
                }
                org.openelisglobal.common.log.LogEvent.logInfo(this.getClass().getSimpleName(), "getUserTestSections",
                        "User " + systemUserId + " roleId=" + roleId + ", userLabUnits=" + userLabUnits);
                List<IdValuePair> allTestSections = DisplayListService.getInstance()
                        .getList(ListType.TEST_SECTION_ACTIVE);
                if (hasGlobalTestSectionAccess || userLabUnits.contains(UnifiedSystemUserController.ALL_LAB_UNITS)) {
                    org.openelisglobal.common.log.LogEvent.logInfo(this.getClass().getSimpleName(),
                            "getUserTestSections",
                            "User has global or AllLabUnits access, returning all " + allTestSections.size()
                                    + " test sections");
                    return allTestSections;
                } else {
                    userTestSections = allTestSections.stream()
                            .filter(testSection -> userLabUnits.contains(testSection.getId()))
                            .collect(Collectors.toList());
                    org.openelisglobal.common.log.LogEvent.logInfo(this.getClass().getSimpleName(),
                            "getUserTestSections", "User has " + userLabUnits.size() + " lab units, returning "
                                    + userTestSections.size() + " test sections");
                    return userTestSections;
                }
            } else if (principal instanceof DefaultSaml2AuthenticatedPrincipal
                    || principal instanceof DefaultOAuth2User) {
                List<IdValuePair> internalSections = getUserTestSectionsFromInternalLabRoles(systemUserId, roleId);
                if (!internalSections.isEmpty() || !samlLegacyRoleFallback) {
                    return internalSections;
                }

                List<IdValuePair> testSections = new ArrayList<>();

                for (GrantedAuthority authority : authentication.getAuthorities()) {
                    ParsedSamlRole parsedRole = SamlRoleMapping.parseAuthority(authority.getAuthority());
                    if (parsedRole == null || parsedRole.getLabScope() == null) {
                        continue;
                    }
                    if (roleId != null && !roleService.get(roleId).getName().trim().equals(parsedRole.getInternalRoleName())) {
                        continue;
                    }

                    List<IdValuePair> allTestSections = DisplayListService.getInstance().getList(ListType.TEST_SECTION_ACTIVE);
                    if (UnifiedSystemUserController.ALL_LAB_UNITS.equalsIgnoreCase(parsedRole.getLabScope())
                            || "all-lab-units".equals(normalizeLabScope(parsedRole.getLabScope()))) {
                        return allTestSections;
                    }

                    List<IdValuePair> userTestSections = allTestSections.stream()
                            .filter(testSection -> normalizeLabScope(testSection.getValue())
                                    .equals(normalizeLabScope(parsedRole.getLabScope())))
                            .collect(Collectors.toList());
                    testSections.addAll(userTestSections);
                }
                if (!testSections.isEmpty()) {
                    return testSections;
                }

                return getUserTestSectionsFromInternalLabRoles(systemUserId, roleId);
            }
        }
        LogEvent.logWarn(this.getClass().getSimpleName(), "getUserTestSections",
                "no principal object in spring security context. Could not get tests belonging to user");
        return new ArrayList<>();

    }

    private List<IdValuePair> getUserTestSectionsFromInternalLabRoles(String systemUserId, String roleId) {
        if (hasGlobalTestSectionAccess(systemUserId)) {
            return DisplayListService.getInstance().getList(ListType.TEST_SECTION_ACTIVE);
        }

        List<String> userLabUnits = new ArrayList<>();
        UserLabUnitRoles userLabRoles = getUserLabUnitRoles(systemUserId);
        if (userLabRoles != null) {
            userLabRoles.getLabUnitRoleMap().forEach(roles -> {
                if (roleId == null || roles.getRoles().contains(roleId)) {
                    userLabUnits.add(roles.getLabUnit());
                }
            });
        }

        List<IdValuePair> allTestSections = DisplayListService.getInstance().getList(ListType.TEST_SECTION_ACTIVE);
        if (userLabUnits.contains(UnifiedSystemUserController.ALL_LAB_UNITS)) {
            return allTestSections;
        }
        return allTestSections.stream().filter(testSection -> userLabUnits.contains(testSection.getId()))
                .collect(Collectors.toList());
    }

    private boolean hasGlobalTestSectionAccess(String systemUserId) {
        return userPermissionService.hasPermission(systemUserId, SystemPermission.GLOBAL_ADMIN)
                || userPermissionService.hasPermission(systemUserId, SystemPermission.ADMINISTRATION);
    }

    private String normalizeLabScope(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase().replace('_', '-').replace(' ', '-');
    }

    @Override
    public List<IdValuePair> getUserSampleTypes(String systemUserId, String roleName) {
        String resultsRoleId = resolveScopedRoleId(systemUserId, roleName);
        List<IdValuePair> testSections = getUserTestSections(systemUserId, resultsRoleId);
        List<Integer> testUnitIds = new ArrayList<>();
        if (testSections != null) {
            testSections.forEach(testSection -> testUnitIds.add(Integer.valueOf(testSection.getId())));
        }

        List<Test> allTests = testService.getTestsByTestSectionIds(testUnitIds);
        return getSampleTypesForTests(allTests);
    }

    @Override
    public List<IdValuePair> getUserSampleTypes(String systemUserId, String roleName, String testSectionName) {
        String resultsRoleId = resolveScopedRoleId(systemUserId, roleName);
        List<IdValuePair> testSections = getUserTestSections(systemUserId, resultsRoleId);
        TestSection testSection = testSectionService.getTestSectionByName(testSectionName);
        // List<String> testUnitIds = new ArrayList<>();
        List<Integer> testUnitIds = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(testSection)) {
            testSections.forEach(testSection2 -> testUnitIds.add(Integer.valueOf(testSection2.getId())));
            // testUnitIds=
            // testSections.stream().filter(el->el.getId().equals(testSection.getId())).map(e->e.getId()).collect(Collectors.toList());
        }
        List<Test> allTests = testService.getTestsByTestSectionIds(testUnitIds);
        return getSampleTypesForTests(allTests);
    }

    private List<IdValuePair> getSampleTypesForTests(List<Test> tests) {
        if (tests == null || tests.isEmpty()) {
            return new ArrayList<>();
        }

        // clear cache to create a fresh Map of testId To TypeOfSample
        typeOfSampleService.clearCache();
        Map<String, String> sampleTypeById = new java.util.LinkedHashMap<>();

        for (Test test : tests) {
            List<TypeOfSample> sampleTypes = typeOfSampleService.getTypeOfSampleForTest(test.getId());
            if (sampleTypes == null) {
                continue;
            }
            for (TypeOfSample type : sampleTypes) {
                if (type == null || StringUtils.isBlank(type.getId()) || !type.getIsActive()) {
                    continue;
                }
                sampleTypeById.putIfAbsent(type.getId(), resolveSampleTypeDisplayName(type));
            }
        }

        return sampleTypeById.entrySet().stream().map(entry -> new IdValuePair(entry.getKey(), entry.getValue()))
                .sorted((left, right) -> StringUtils.defaultString(left.getValue())
                        .compareToIgnoreCase(StringUtils.defaultString(right.getValue())))
                .collect(Collectors.toList());
    }

    private String resolveSampleTypeDisplayName(TypeOfSample type) {
        String localizedName = StringUtils.trimToNull(type.getLocalizedName());
        if (localizedName != null) {
            return localizedName;
        }

        String englishName =
                type.getLocalization() != null ? StringUtils.trimToNull(type.getLocalization().getEnglish()) : null;
        if (englishName != null) {
            return englishName;
        }

        String description = StringUtils.trimToNull(type.getDescription());
        if (description != null) {
            return description;
        }

        String localAbbreviation = StringUtils.trimToNull(type.getLocalAbbreviation());
        return localAbbreviation != null ? localAbbreviation : type.getId();
    }

    @Override
    public List<TestResultItem> filterResultsByLabUnitRoles(String systemUserId, List<TestResultItem> results,
            String roleName) {
        String resultsRoleId = roleService.getRoleByName(roleName).getId();
        List<IdValuePair> testSections = getUserTestSections(systemUserId, resultsRoleId);
        List<Integer> testUnitIds = new ArrayList<>();
        if (testSections != null) {
            testSections.forEach(testSection -> testUnitIds.add(Integer.valueOf(testSection.getId())));
        }
        org.openelisglobal.common.log.LogEvent.logInfo(this.getClass().getSimpleName(), "filterResultsByLabUnitRoles",
                "User " + systemUserId + " has " + (testSections != null ? testSections.size() : 0) + " test sections: "
                        + testUnitIds);

        List<Test> allTests = testService.getTestsByTestSectionIds(testUnitIds);
        List<String> allTestsIds = new ArrayList<>();
        allTests.forEach(test -> allTestsIds.add(test.getId()));
        // Log which test IDs are in the results and which are allowed
        List<String> resultTestIds = results.stream().map(r -> r.getTestId()).collect(Collectors.toList());
        org.openelisglobal.common.log.LogEvent.logInfo(this.getClass().getSimpleName(), "filterResultsByLabUnitRoles",
                "Input results: " + results.size() + " (test IDs: " + resultTestIds + "), Allowed test IDs: "
                        + allTestsIds.size() + ", Filtered results: "
                        + results.stream().filter(result -> allTestsIds.contains(result.getTestId())).count());
        return results.stream().filter(result -> allTestsIds.contains(result.getTestId())).collect(Collectors.toList());
    }

    @Override
    public List<IdValuePair> getAllDisplayUserTestsByLabUnit(String SystemUserId, String roleName) {
        String resultsRoleId = roleService.getRoleByName(roleName).getId();
        List<IdValuePair> testSections = getUserTestSections(SystemUserId, resultsRoleId);
        List<Integer> testUnitIds = new ArrayList<>();
        if (testSections != null) {
            testSections.forEach(testSection -> testUnitIds.add(Integer.valueOf(testSection.getId())));
        }

        List<Test> allTests = testService.getTestsByTestSectionIds(testUnitIds);
        List<String> allTestsIds = new ArrayList<>();
        allTests.forEach(test -> allTestsIds.add(test.getId()));

        List<IdValuePair> allDisplayUserTests = DisplayListService.getInstance()
                .getListWithLeadingBlank(DisplayListService.ListType.ALL_TESTS);
        return allDisplayUserTests.stream().filter(test -> allTestsIds.contains(test.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AnalysisItem> filterAnalysisResultsByLabUnitRoles(String SystemUserId, List<AnalysisItem> results,
            String roleName) {
        String resultsRoleId = roleService.getRoleByName(roleName).getId();
        List<IdValuePair> testSections = getUserTestSections(SystemUserId, resultsRoleId);
        List<Integer> testUnitIds = new ArrayList<>();
        if (testSections != null) {
            testSections.forEach(testSection -> testUnitIds.add(Integer.valueOf(testSection.getId())));
        }

        List<Test> allTests = testService.getTestsByTestSectionIds(testUnitIds);
        List<String> allTestsIds = new ArrayList<>();
        allTests.forEach(test -> allTestsIds.add(test.getId()));
        return results.stream().filter(result -> allTestsIds.contains(result.getTestId())).collect(Collectors.toList());
    }

    @Override
    public List<Analysis> filterAnalysesByLabUnitRoles(String SystemUserId, List<Analysis> results, String roleName) {
        String resultsRoleId = roleService.getRoleByName(roleName).getId();
        List<IdValuePair> testSections = getUserTestSections(SystemUserId, resultsRoleId);
        List<Integer> testUnitIds = new ArrayList<>();
        if (testSections != null) {
            testSections.forEach(testSection -> testUnitIds.add(Integer.valueOf(testSection.getId())));
        }

        List<Test> allTests = testService.getTestsByTestSectionIds(testUnitIds);
        List<String> allTestsIds = new ArrayList<>();
        allTests.forEach(test -> allTestsIds.add(test.getId()));
        return results.stream().filter(result -> allTestsIds.contains(result.getTest().getId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<IdValuePair> getUserPrograms(String systemUserId, String userRole) {
        String resultsRoleId = resolveScopedRoleId(systemUserId, userRole);
        List<IdValuePair> testSections = getUserTestSections(systemUserId, resultsRoleId);
        List<String> testUnitIds = new ArrayList<>();
        if (testSections != null) {
            testSections.forEach(testSection -> testUnitIds.add(testSection.getId()));
        }

        List<IdValuePair> allPrograms = DisplayListService.getInstance().getList(ListType.PROGRAM);
        return allPrograms.stream().filter(p -> {
            var program = programService.get(p.getId());
            if (program == null) {
                return false;
            }
            if (program.getTestSection() != null) {
                return testUnitIds.contains(program.getTestSection().getId());
            }
            return isRoutineProgram(program, p);
        }).collect(Collectors.toList());
    }

    private boolean isRoutineProgram(org.openelisglobal.program.valueholder.Program program, IdValuePair displayValue) {
        String programCode = StringUtils.defaultString(program.getCode());
        String programName = StringUtils.defaultString(program.getProgramName());
        String label = displayValue == null ? "" : StringUtils.defaultString(displayValue.getValue());

        return "RTN_Id".equalsIgnoreCase(programCode) || "Routine Testing".equalsIgnoreCase(programName)
                || "Routine Testing".equalsIgnoreCase(label) || "Routine".equalsIgnoreCase(programName)
                || "Routine".equalsIgnoreCase(label);
    }

    private String resolveScopedRoleId(String systemUserId, String roleName) {
        if (StringUtils.isBlank(roleName)) {
            return null;
        }

        var role = roleService.getRoleByName(roleName);
        if (role == null) {
            return null;
        }

        String roleId = role.getId();
        if (Constants.ROLE_RECEPTION.equals(roleName) && !userHasLabUnitRole(systemUserId, roleId)
                && userPermissionService.hasPermission(systemUserId, SystemPermission.ORDER)) {
            return null;
        }

        return roleId;
    }

    private boolean userHasLabUnitRole(String systemUserId, String roleId) {
        if (StringUtils.isBlank(systemUserId) || StringUtils.isBlank(roleId)) {
            return false;
        }

        UserLabUnitRoles userLabRoles = getUserLabUnitRoles(systemUserId);
        if (userLabRoles == null || userLabRoles.getLabUnitRoleMap() == null) {
            return false;
        }

        return userLabRoles.getLabUnitRoleMap().stream().map(LabUnitRoleMap::getRoles).filter(roleIds -> roleIds != null)
                .anyMatch(roleIds -> roleIds.contains(roleId));
    }

}
