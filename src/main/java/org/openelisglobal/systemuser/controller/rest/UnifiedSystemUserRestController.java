package org.openelisglobal.systemuser.controller.rest;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.controller.BaseController;
import org.openelisglobal.common.exception.LIMSDuplicateRecordException;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.provider.validation.PasswordValidationFactory;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.services.DisplayListService.ListType;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.common.util.StringUtil;
import org.openelisglobal.common.validator.BaseErrors;
import org.openelisglobal.login.dao.UserModuleService;
import org.openelisglobal.login.service.LoginUserService;
import org.openelisglobal.login.valueholder.LoginUser;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.role.action.bean.DisplayRole;
import org.openelisglobal.role.service.CustomRoleDefinitionService;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.systemuser.form.UnifiedSystemUserForm;
import org.openelisglobal.systemuser.service.ProfessionalProfileRecipientService;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.service.UserService;
import org.openelisglobal.systemuser.validator.UnifiedSystemUserFormValidator;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.systemuser.valueholder.UnifiedSystemUser;
import org.openelisglobal.test.service.TestSectionService;
import org.openelisglobal.userrole.service.UserRoleService;
import org.openelisglobal.userrole.valueholder.LabUnitRoleMap;
import org.openelisglobal.userrole.valueholder.UserLabUnitRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest")
public class UnifiedSystemUserRestController extends BaseController {

    private static final String YES = "Y";
    private static final String NO = "N";
    public static final String ALL_LAB_UNITS = "AllLabUnits";
    private static final String RESERVED_ADMIN_NAME = "admin";
    // private static final String GLOBAL_ADMIN_ID = "globalAdminId";
    // private static final String ID = "id";
    public static final char DEFAULT_OBFUSCATED_CHARACTER = '@';

    private static final String[] ALLOWED_FIELDS = new String[] { "systemUserId", "loginUserId", "userLoginName",
            "userPassword", "confirmPassword", "userFirstName", "userLastName", "expirationDate", "timeout",
            "accountLocked", "accountDisabled", "accountActive", "selectedRoles*", "selectedLabUnitRoles",
            "selectedCustomRoleIds*", "testSectionId", "systemUsers", "systemUserIdToCopy", "allowCopyUserRoles",
            "linkedProviderPersonId", "professionalProfileCode", "signatureImageData", "signatureImageContentType" };

    @Autowired
    private UnifiedSystemUserFormValidator formValidator;

    @Autowired
    private LoginUserService loginService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private CustomRoleDefinitionService customRoleDefinitionService;
    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private UserModuleService userModuleService;
    @Autowired
    private UserService userService;
    @Autowired
    private ProviderService providerService;
    @Autowired
    private TestSectionService testSectionService;
    @Autowired
    private ProfessionalProfileRecipientService professionalProfileRecipientService;
    // private static final String RESERVED_ADMIN_NAME = "admin";

    private static String GLOBAL_ADMIN_ID;
    private static String ID;
    // public static final char DEFAULT_OBFUSCATED_CHARACTER = '@';
    // public static final String ALL_LAB_UNITS = "AllLabUnits";

    @PostConstruct
    private void initialize() {
        Role globalAdmin = roleService.getRoleByName(Constants.ROLE_GLOBAL_ADMIN);
        if (globalAdmin == null) {
            // Some DB seeds use "Admin" instead of "Global Administrator".
            LogEvent.logWarn(this.getClass().getSimpleName(), "initialize",
                    "Role '" + Constants.ROLE_GLOBAL_ADMIN + "' not found; falling back to 'Admin'");
            globalAdmin = roleService.getRoleByName("Admin");
        }

        if (globalAdmin == null) {
            LogEvent.logError(this.getClass().getSimpleName(), "initialize",
                    "No suitable global admin role found (expected '" + Constants.ROLE_GLOBAL_ADMIN + "' or 'Admin')");
            GLOBAL_ADMIN_ID = null;
            return;
        }

        GLOBAL_ADMIN_ID = globalAdmin.getId();
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @GetMapping(value = "/users/{roleName}")
    @ResponseBody
    public List<IdValuePair> getUsersWithRole(@PathVariable String roleName) {
        List<SystemUser> users = systemUserService.getAll();
        return users.stream().filter(e -> userRoleService.userInRole(e.getId(), roleName))
                .map(e -> new IdValuePair(e.getId(), e.getDisplayName())).collect(Collectors.toList());
    }

    @GetMapping(value = "/users")
    @ResponseBody
    public List<IdValuePair> getUsersWithRole() {
        List<SystemUser> users = systemUserService.getAll();
        List<IdValuePair> idValues = users.stream().map(e -> new IdValuePair(e.getId(), e.getDisplayName()))
                .collect(Collectors.toList());
        return idValues;
    }

    @GetMapping(value = "/UnifiedSystemUser/login-name-status")
    @ResponseBody
    public Map<String, Object> getLoginNameStatus(
            @RequestParam(name = "loginName", defaultValue = "") String loginName,
            @RequestParam(name = "loginUserId", required = false) String loginUserId) {
        String normalizedLoginName = StringUtils.trimToEmpty(loginName);
        String normalizedLoginUserId = StringUtils.trimToNull(loginUserId);

        Map<String, Object> response = new HashMap<>();
        response.put("loginName", normalizedLoginName);

        if (StringUtils.isBlank(normalizedLoginName)) {
            response.put("available", false);
            response.put("duplicate", false);
            return response;
        }

        LoginUser existingLogin = loginService.getMatch("loginName", normalizedLoginName).orElse(null);
        boolean duplicate = existingLogin != null
                && (normalizedLoginUserId == null || !normalizedLoginUserId.equals(String.valueOf(existingLogin.getId())));

        response.put("available", !duplicate);
        response.put("duplicate", duplicate);
        return response;
    }

    @GetMapping(value = "/users/professional-profile/{profileCode}")
    @ResponseBody
    public List<IdValuePair> getUsersByProfessionalProfile(
            @PathVariable String profileCode,
            @RequestParam(name = "activeOnly", defaultValue = "true") boolean activeOnly,
            @RequestParam(name = "labelMode", defaultValue = "professional") String labelMode,
            @RequestParam(name = "requireEmail", defaultValue = "true") boolean requireEmail) {
        final String normalizedProfileCode = professionalProfileRecipientService
                .normalizeProfessionalProfileCode(profileCode);
        if (StringUtils.isBlank(normalizedProfileCode)) {
            return Collections.emptyList();
        }
        boolean useFullNameLabel = "fullName".equalsIgnoreCase(StringUtils.trimToEmpty(labelMode));
        return professionalProfileRecipientService.getEligibleUserOptionsForProfessionalProfile(normalizedProfileCode,
                activeOnly, useFullNameLabel, requireEmail);
    }

    @GetMapping(value = "/UnifiedSystemUser")
    public ResponseEntity<UnifiedSystemUserForm> showUnifiedSystemUser(HttpServletRequest request,
            @RequestParam(name = "ID", defaultValue = "") String id)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        UnifiedSystemUserForm form = new UnifiedSystemUserForm();
        form.setFormAction("UnifiedSystemUser");
        form.setCancelAction("UnifiedSystemUserMenu");

        boolean doFiltering = true;
        request.setAttribute(ALLOW_EDITS_KEY, "true");
        request.setAttribute(PREVIOUS_DISABLED, "true");
        request.setAttribute(NEXT_DISABLED, "true");
        request.setAttribute(DISPLAY_PREV_NEXT, false);

        boolean isNew = GenericValidator.isBlankOrNull(id) || "0".equals(id);

        setDefaultProperties(form);
        if (!isNew) {
            setPropertiesForExistingUser(form, id, doFiltering);
        }
        setupRoles(form, request, doFiltering);

        // load testSections for drop down
        List<IdValuePair> testSections = DisplayListService.getInstance().getList(ListType.TEST_SECTION_ACTIVE);
        form.setTestSections(testSections);
        form.setPractitionerPersons(DisplayListService.getInstance().getList(ListType.PRACTITIONER_PERSONS));
        form.setSystemUsers(getDisplaySystemUsersJsonArray());
        addFlashMsgsToRequest(request);
        // return findForward(FWD_SUCCESS, form);
        return ResponseEntity.ok(form);
    }

    private void setupRoles(UnifiedSystemUserForm form, HttpServletRequest request, boolean doFiltering) {
        List<Role> roles = getAllRoles();
        doFiltering &= !userModuleService.isUserAdmin(request);

        if (doFiltering) {
            roles = doRoleFiltering(roles, getSysUserId(request));
        }

        List<DisplayRole> displayRoles = convertToDisplayRoles(roles);
        displayRoles = sortAndGroupRoles(displayRoles);
        String globalParentRoleId = roleService.getRoleByName(Constants.GLOBAL_ROLES_GROUP).getId();
        String labUnitRoleId = roleService.getRoleByName(Constants.LAB_ROLES_GROUP).getId();

        List<DisplayRole> globalRoles = displayRoles.stream().filter(role -> role.getParentRole() != null)
                .filter(role -> role.getParentRole().equals(globalParentRoleId)).collect(Collectors.toList());
        List<DisplayRole> labUnitRoles = displayRoles.stream().filter(role -> role.getParentRole() != null)
                .filter(role -> role.getParentRole().equals(labUnitRoleId)).collect(Collectors.toList());
        form.setGlobalRoles(globalRoles);
        form.setLabUnitRoles(labUnitRoles);
        form.setCustomRoles(convertToDisplayRoles(customRoleDefinitionService.getCustomRoles()));
    }

    private List<DisplayRole> convertToDisplayRoles(List<Role> roles) {
        int elementCount = 0;

        List<DisplayRole> displayRoles = new ArrayList<>();

        for (Role role : roles) {
            elementCount++;
            displayRoles.add(convertToDisplayRole(role, elementCount));
        }

        return displayRoles;
    }

    private DisplayRole convertToDisplayRole(Role role, int count) {
        DisplayRole displayRole = new DisplayRole();

        displayRole.setRoleName(role.getLocalizedName());
        displayRole.setElementID(String.valueOf(count));
        displayRole.setRoleId(role.getId());
        displayRole.setGroupingRole(role.getGroupingRole());
        displayRole.setParentRole(role.getGroupingParent());

        return displayRole;
    }

    private List<DisplayRole> sortAndGroupRoles(List<DisplayRole> roles) {
        /*
         * The sorting we want to end up with is first alphabetical and then by groups
         * What makes things a little more difficult is that we may have roles which
         * have parents which don't exist, we shouldn't but we might. So... First sweep
         * is to find all the orphaned roles and set their parents to null Then move all
         * the first generation groups to a new list. Then scan for all for all groups
         * and move their members, repeat until the first list is empty, which is why we
         * didn't want orphans. Lastly we will add the role ID as a child to all of it's
         * parents
         */

        Collections.sort(roles, new Comparator() {
            @Override
            public int compare(Object obj1, Object obj2) {
                DisplayRole role1 = (DisplayRole) obj1;
                DisplayRole role2 = (DisplayRole) obj2;
                return role1.getRoleName().toUpperCase().compareTo(role2.getRoleName().toUpperCase());
            }
        });

        /*
         * The reason we're not making a map is that we want to preserve the order
         * during this whole process
         */
        List<String> groupIds = new ArrayList<>();

        for (DisplayRole role : roles) {
            if (role.isGroupingRole()) {
                groupIds.add(role.getRoleId());
            }
        }

        for (DisplayRole role : roles) {
            if (!GenericValidator.isBlankOrNull(role.getParentRole()) && !groupIds.contains(role.getParentRole())) {
                role.setParentRole(null);
            }
        }

        List<DisplayRole> mergeList = new ArrayList<>();
        List<DisplayRole> currentWorkingList = new ArrayList<>();
        List<DisplayRole> unplacedList = new ArrayList<>();

        for (DisplayRole role : roles) {
            if (GenericValidator.isBlankOrNull(role.getParentRole())) {
                role.setNestingLevel(0);
                currentWorkingList.add(role);
            } else {
                unplacedList.add(role);
            }
        }

        int indentCount = 0;
        while (unplacedList.size() > 0) {
            indentCount++;
            for (DisplayRole placedRole : currentWorkingList) {
                mergeList.add(placedRole);

                if (placedRole.isGroupingRole()) {
                    List<DisplayRole> removeList = new ArrayList<>();
                    for (DisplayRole unplacedRole : unplacedList) {
                        if (unplacedRole.getParentRole().equals(placedRole.getRoleId())) {
                            unplacedRole.setNestingLevel(indentCount);
                            mergeList.add(unplacedRole);
                            removeList.add(unplacedRole);
                            placedRole.addChildID(unplacedRole.getRoleId());
                        }
                    }
                    unplacedList.removeAll(removeList);
                }
            }

            currentWorkingList = mergeList;
            mergeList = new ArrayList<>();
        }

        /*
         * For finding all parents we are going to iterate backwards since all parents
         * are in front of children role
         */
        for (int i = currentWorkingList.size() - 1; i > 0; i--) {
            DisplayRole role = currentWorkingList.get(i);

            if (!GenericValidator.isBlankOrNull(role.getParentRole())) {
                String roleID = role.getRoleId();
                String currentParentID = role.getParentRole();

                for (int parent = i - 1; parent >= 0; parent--) {
                    if (currentWorkingList.get(parent).getRoleId().equals(currentParentID)) {
                        DisplayRole parentRole = currentWorkingList.get(parent);

                        parentRole.addChildID(roleID);

                        if (GenericValidator.isBlankOrNull(parentRole.getParentRole())) {
                            break;
                        } else {
                            currentParentID = parentRole.getParentRole();
                        }
                    }
                }
            }
        }

        return currentWorkingList;
    }

    private List<Role> doRoleFiltering(List<Role> roles, String loggedInUserId) {

        List<String> rolesForLoggedInUser = userRoleService.getRoleIdsForUser(loggedInUserId);

        if (!rolesForLoggedInUser.contains(GLOBAL_ADMIN_ID)) {
            List<Role> tmpRoles = new ArrayList<>();

            for (Role role : roles) {
                if (!GLOBAL_ADMIN_ID.equals(role.getId())) {
                    tmpRoles.add(role);
                }
            }

            roles = tmpRoles;
        }

        return roles;
    }

    private void setDefaultProperties(UnifiedSystemUserForm form)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        String expireDate = getYearsFromNow(10);
        form.setExpirationDate(expireDate);
        form.setTimeout("480");
        form.setSystemUserLastupdated(new Timestamp(System.currentTimeMillis()));
    }

    private void setPropertiesForExistingUser(UnifiedSystemUserForm form, String id, boolean doFiltering)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        LoginUser login = getLoginFromCombinedId(id);
        SystemUser systemUser = getSystemUserFromCombinedId(id);

        if (login != null) {
            String proxyPassword = getProxyPassword(login);
            form.setLoginUserId(Integer.toString(login.getId()));
            form.setAccountDisabled(login.getAccountDisabled());
            form.setAccountLocked(login.getAccountLocked());
            form.setUserLoginName(login.getLoginName());
            form.setUserPassword(proxyPassword);
            form.setConfirmPassword(proxyPassword);
            form.setExpirationDate(login.getPasswordExpiredDateForDisplay());
            form.setTimeout(login.getUserTimeOut());
        }

        if (systemUser != null) {
            form.setSystemUserId(systemUser.getId());
            form.setUserFirstName(systemUser.getFirstName());
            form.setUserLastName(systemUser.getLastName());
            form.setLinkedProviderPersonId(StringUtils.defaultString(systemUser.getLinkedProviderPersonId()));
            form.setProfessionalProfileCode(StringUtils.defaultString(systemUser.getProfessionalProfileCode()));
            form.setSignatureImageData(systemUser.getSignatureImageData());
            form.setSignatureImageContentType(StringUtils.defaultString(systemUser.getSignatureImageContentType()));
            form.setAccountActive(systemUser.getIsActive());
            form.setSystemUserLastupdated(systemUser.getLastupdated());

            List<String> roleIds = userRoleService.getRoleIdsForUser(systemUser.getId());
            String globalParentRoleId = roleService.getRoleByName(Constants.GLOBAL_ROLES_GROUP).getId();
            List<String> globalRoleIds = getAllRoles().stream().filter(role -> role.getGroupingParent() != null)
                    .filter(role -> role.getGroupingParent().equals(globalParentRoleId)).map(role -> role.getId())
                    .collect(Collectors.toList());
            List<String> customRoleIds = customRoleDefinitionService.getCustomRoles().stream().map(Role::getId)
                    .collect(Collectors.toList());
            List<String> globalSelectedRoleIds = roleIds.stream().filter(role -> globalRoleIds.contains(role))
                    .collect(Collectors.toList());
            List<String> selectedCustomRoleIds = roleIds.stream().filter(role -> customRoleIds.contains(role))
                    .collect(Collectors.toList());
            setLabunitRolesForExistingUser(form);
            form.setSelectedRoles(globalSelectedRoleIds);
            form.setSelectedCustomRoleIds(selectedCustomRoleIds);
            // is this meant to be returned?
//            doFiltering = !roleIds.contains(MAINTENANCE_ADMIN_ID);
        }

    }

    private String getProxyPassword(LoginUser login) {
        char[] chars = new char[9];
        Arrays.fill(chars, DEFAULT_OBFUSCATED_CHARACTER);
        return new String(chars);
        // return StringUtil.replaceAllChars(login.getPassword(),
        // DEFAULT_PASSWORD_FILLER);
    }

    private LoginUser getLoginFromCombinedId(String id) {
        LoginUser login = null;
        Integer loginId = UnifiedSystemUser.getLoginUserIDFromCombinedID(id);

        if (null != loginId) {
            login = loginService.get(loginId);
        }

        return login;
    }

    private SystemUser getSystemUserFromCombinedId(String id) {
        SystemUser systemUser = null;
        String systemUserId = UnifiedSystemUser.getSystemUserIDFromCombinedID(id);

        if (!GenericValidator.isBlankOrNull(systemUserId)) {
            systemUser = systemUserService.get(systemUserId);
        }

        return systemUser;
    }

    private String getYearsFromNow(int years) {
        Calendar today = Calendar.getInstance();

        today.add(Calendar.YEAR, years);

        return DateUtil.formatDateAsText(today.getTime());
    }

    private List<Role> getAllRoles() {
        return roleService.getAllActiveRoles().stream().filter(this::isVisibleRole).toList();
    }

    private boolean isVisibleRole(Role role) {
        String roleName = role == null ? null : role.getName();
        return !Constants.ROLE_VALIDATION_BIOLOGIST.equals(roleName)
                && !Constants.ROLE_VALIDATION_MEDICAL.equals(roleName);
    }

    @PostMapping(value = "/UnifiedSystemUser")
    public Map<String, String> showUpdateUnifiedSystemUser(HttpServletRequest request,
            @RequestBody @Valid UnifiedSystemUserForm form, BindingResult result) {
        boolean doFiltering = true;
        formValidator.validate(form, result);
        Map<String, String> response = new HashMap<>();

        if (result.hasErrors()) {
            saveErrors(result);
            setupRoles(form, request, doFiltering);
            // return findForward(FWD_FAIL_INSERT, form);
            response.put("forward", findForward(FWD_FAIL_INSERT));
            // return response;
            // return findForward(FWD_FAIL_INSERT);
        }

        request.setAttribute(ALLOW_EDITS_KEY, "true");
        request.setAttribute(PREVIOUS_DISABLED, "false");
        request.setAttribute(NEXT_DISABLED, "false");

        if (form.getUserLoginName() != null) {
            form.setUserLoginName(form.getUserLoginName().trim());
        } else {
            form.setUserLoginName("");
        }

        String forward = validateAndUpdateSystemUser(request, form);

        if (forward.equals(FWD_SUCCESS_INSERT)) {
            // redirectAttributes.addFlashAttribute(FWD_SUCCESS, true);
            Map<String, String> params = new HashMap<>();
            params.put("forward", FWD_SUCCESS);
            params.put("ID", ID);
            // return getForwardWithParameters(findForward(forward, form), params);
            // redirectAttributes.addFlashAttribute("ID", ID);
            // return "redirect:/UnifiedSystemUser";
            response.put("forward", "redirect:/UnifiedSystemUser");
        } else {
            setupRoles(form, request, doFiltering);
            // return findForward(forward);
            response.put("forward", findForward(forward));
        }

        return response;
    }

    private String validateAndUpdateSystemUser(HttpServletRequest request, UnifiedSystemUserForm form) {
        String loginUserId = form.getLoginUserId();
        String systemUserId = form.getSystemUserId();

        Errors errors = new BaseErrors();

        boolean loginUserNew = GenericValidator.isBlankOrNull(loginUserId);
        boolean systemUserNew = GenericValidator.isBlankOrNull(systemUserId);
        boolean passwordUpdated = false;

        passwordUpdated = passwordHasBeenUpdated(loginUserNew, form);
        validateUser(form, errors, loginUserNew, passwordUpdated, loginUserId);
        validateLinkedProviderProfileConsistency(form, errors);

        if (errors.hasErrors()) {
            saveErrors(errors);
            return FWD_FAIL_INSERT;
        }

        String loggedOnUserId = getSysUserId(request);
        List<String> selectedCustomRoleIds = normalizedRoleIds(form.getSelectedCustomRoleIds());
        List<String> derivedCustomPermissionRoleIds = selectedCustomRoleIds.stream()
                .flatMap(roleId -> customRoleDefinitionService.getPermissionRoleIdsForCustomRole(roleId).stream())
                .collect(Collectors.toList());
        List<String> effectiveGlobalRoles = new ArrayList<>();
        effectiveGlobalRoles.addAll(normalizedRoleIds(form.getSelectedRoles()));
        effectiveGlobalRoles.addAll(selectedCustomRoleIds);
        effectiveGlobalRoles.addAll(getGlobalRoleIds(derivedCustomPermissionRoleIds));

        LoginUser loginUser = createLoginUser(form, loginUserId, loginUserNew, passwordUpdated, loggedOnUserId);
        SystemUser systemUser = createSystemUser(form, systemUserId, systemUserNew, loggedOnUserId);
        try {
            if (form.getAllowCopyUserRoles().equals(NO)) {
                userService.updateLoginUser(loginUser, loginUserNew, systemUser, systemUserNew,
                        deduplicateRoleIds(effectiveGlobalRoles), loggedOnUserId);
                saveUserLabUnitRoles(systemUser, form, loggedOnUserId, selectedCustomRoleIds,
                        derivedCustomPermissionRoleIds);
            } else if (form.getAllowCopyUserRoles().equals(YES)) {
                if (StringUtils.isNotBlank(form.getSystemUserIdToCopy().trim())) {
                    String globalParentRoleId = roleService.getRoleByName(Constants.GLOBAL_ROLES_GROUP).getId();
                    List<String> globaRolesIds = getAllRoles().stream().filter(role -> role.getGroupingParent() != null)
                            .filter(role -> role.getGroupingParent().equals(globalParentRoleId))
                            .map(role -> role.getId()).collect(Collectors.toList());
                    List<String> customRoleIds = customRoleDefinitionService.getCustomRoles().stream().map(Role::getId)
                            .collect(Collectors.toList());
                    List<String> copiedRoleIds = userRoleService.getRoleIdsForUser(form.getSystemUserIdToCopy().trim());
                    List<String> globalCopiedRoleIds = copiedRoleIds.stream()
                            .filter(role -> globaRolesIds.contains(role) || customRoleIds.contains(role))
                            .collect(Collectors.toList());

                    userService.updateLoginUser(loginUser, loginUserNew, systemUser, systemUserNew, globalCopiedRoleIds,
                            loggedOnUserId);

                    UserLabUnitRoles labRoles = userService.getUserLabUnitRoles(form.getSystemUserIdToCopy().trim());
                    Map<String, Set<String>> copiedLabUnitRolesMap = new HashMap<>();
                    labRoles.getLabUnitRoleMap().forEach(roleMap -> copiedLabUnitRolesMap
                            .put(new String(roleMap.getLabUnit()), new HashSet<>(roleMap.getRoles())));
                    userService.saveUserLabUnitRoles(systemUser, copiedLabUnitRolesMap, loggedOnUserId);
                }
            }
            ID = systemUser.getId() + "-" + loginUser.getId();
        } catch (LIMSRuntimeException e) {
            if (e.getCause() instanceof org.hibernate.StaleObjectStateException) {
                errors.reject("errors.OptimisticLockException", "errors.OptimisticLockException");
            } else if (e.getCause() instanceof LIMSDuplicateRecordException) {
                errors.reject("errors.DuplicateRecordException", "errors.DuplicateRecordException");
            } else {
                errors.reject("errors.UpdateException", "errors.UpdateException");
            }

            saveErrors(errors);
            disableNavigationButtons(request);
            return FWD_FAIL_INSERT;
        }

        return FWD_SUCCESS_INSERT;
    }

    private boolean passwordHasBeenUpdated(boolean loginUserNew, UnifiedSystemUserForm form) {
        if (loginUserNew) {
            return true;
        }

        String password = form.getUserPassword();

        return !StringUtil.containsOnly(password, DEFAULT_OBFUSCATED_CHARACTER);
    }

    private void validateUser(UnifiedSystemUserForm form, Errors errors, boolean loginUserIsNew,
            boolean passwordUpdated, String loginUserId) {
        boolean checkForDuplicateName = loginUserIsNew || userNameChanged(loginUserId, form.getUserLoginName());
        // check login name

        if (GenericValidator.isBlankOrNull(form.getUserLoginName())) {
            errors.reject("errors.loginName.required", "errors.loginName.required");
        } else if (checkForDuplicateName) {
            LoginUser login = loginService.getMatch("loginName", form.getUserLoginName()).orElse(null);
            if (login != null) {
                errors.reject("errors.loginName.duplicated");
            }
        }

        // check first and last name
        if (GenericValidator.isBlankOrNull(form.getUserFirstName())
                || GenericValidator.isBlankOrNull(form.getUserLastName())) {
            errors.reject("errors.userName.required", "errors.userName.required");
        }

        if (passwordUpdated) {
            // check passwords match
            if (GenericValidator.isBlankOrNull(form.getUserPassword())
                    || !form.getUserPassword().equals(form.getConfirmPassword())) {
                errors.reject("errors.password.match", "errors.password.match");
            } else if (!passwordValid(form.getUserPassword())) { // validity
                errors.reject("login.error.password.requirement");
            }
        }

        // check timeout
        if (!timeoutValidAndInRange(form.getTimeout())) {
            errors.reject("errors.timeout.range", "errors.timeout.range");
        }
    }

    private boolean userNameChanged(String loginUserId, String newName) {
        if (GenericValidator.isBlankOrNull(loginUserId)) {
            return false;
        }

        LoginUser login = loginService.get(Integer.parseInt(loginUserId));

        return !newName.equals(login.getLoginName());
    }

    private boolean timeoutValidAndInRange(String timeout) {
        try {
            int timeInMin = Integer.parseInt(timeout);
            return timeInMin > 0 && timeInMin < 601;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean passwordValid(String password) {
        return PasswordValidationFactory.getPasswordValidator().passwordValid(password);
    }

    private void validateLinkedProviderProfileConsistency(UnifiedSystemUserForm form, Errors errors) {
        String linkedProviderPersonId = StringUtils.trimToNull(form.getLinkedProviderPersonId());
        String selectedProfileCode = professionalProfileRecipientService
                .normalizeProfessionalProfileCode(form.getProfessionalProfileCode());

        if (linkedProviderPersonId == null || StringUtils.isBlank(selectedProfileCode)) {
            return;
        }

        Provider linkedProvider = findLinkedActiveProvider(linkedProviderPersonId);

        if (linkedProvider == null) {
            return;
        }

        String providerProfileCode = professionalProfileRecipientService
                .normalizeProfessionalProfileCode(linkedProvider.getProfessionalProfileCode());
        if (StringUtils.isNotBlank(providerProfileCode) && !providerProfileCode.equals(selectedProfileCode)) {
            errors.reject("errors.user.provider.profile.mismatch",
                    "Linked provider profile does not match selected user profile.");
        }
    }

    private LoginUser createLoginUser(UnifiedSystemUserForm form, String loginUserId, boolean loginUserNew,
            boolean passwordUpdated, String loggedOnUserId) {

        LoginUser login = new LoginUser();

        if (!loginUserNew) {
            login = loginService.get(Integer.parseInt(form.getLoginUserId()));
        }
        login.setAccountDisabled(form.getAccountDisabled());
        login.setAccountLocked(form.getAccountLocked());
        login.setLoginName(form.getUserLoginName());
        if (passwordUpdated) {
            login.setPassword(form.getUserPassword());
            loginService.hashPassword(login, login.getPassword());
        }
        login.setPasswordExpiredDateForDisplay(form.getExpirationDate());
        if (RESERVED_ADMIN_NAME.equals(form.getUserLoginName())) {
            login.setIsAdmin("Y");
        } else {
            login.setIsAdmin("N");
        }
        login.setUserTimeOut(form.getTimeout());
        login.setSysUserId(loggedOnUserId);

        return login;
    }

    private SystemUser createSystemUser(UnifiedSystemUserForm form, String systemUserId, boolean systemUserNew,
            String loggedOnUserId) {

        SystemUser systemUser = new SystemUser();

        if (!systemUserNew) {
            systemUser = systemUserService.get(systemUserId);
        }

        String linkedProviderPersonId = StringUtils.trimToNull(form.getLinkedProviderPersonId());
        Provider linkedProvider = findLinkedActiveProvider(linkedProviderPersonId);
        String resolvedFirstName = StringUtils.trimToNull(form.getUserFirstName());
        String resolvedLastName = StringUtils.trimToNull(form.getUserLastName());

        if (linkedProvider != null && linkedProvider.getPerson() != null) {
            Person linkedPerson = linkedProvider.getPerson();
            resolvedFirstName = StringUtils.defaultIfBlank(
                    StringUtils.trimToNull(linkedPerson.getFirstName()), resolvedFirstName);
            resolvedLastName = StringUtils.defaultIfBlank(
                    StringUtils.trimToNull(linkedPerson.getLastName()), resolvedLastName);
        }

        systemUser.setFirstName(resolvedFirstName);
        systemUser.setLastName(resolvedLastName);
        systemUser.setLoginName(form.getUserLoginName());
        systemUser.setIsActive(form.getAccountActive());
        systemUser.setIsEmployee("Y");
        systemUser.setExternalId("1");
        String initial = StringUtils.substring(StringUtils.defaultString(systemUser.getFirstName()), 0, 1)
                + StringUtils.substring(StringUtils.defaultString(systemUser.getLastName()), 0, 1);
        systemUser.setInitials(initial);
        systemUser.setLinkedProviderPersonId(linkedProviderPersonId);
        systemUser.setProfessionalProfileCode(resolveUserProfessionalProfileCode(form, linkedProvider));
        systemUser.setSignatureImageData(StringUtils.trimToNull(form.getSignatureImageData()));
        systemUser.setSignatureImageContentType(StringUtils.trimToNull(form.getSignatureImageContentType()));
        systemUser.setSysUserId(loggedOnUserId);

        return systemUser;
    }

    private String resolveUserProfessionalProfileCode(UnifiedSystemUserForm form, Provider linkedProvider) {
        String selectedProfileCode = StringUtils.trimToNull(form.getProfessionalProfileCode());
        if (selectedProfileCode != null) {
            return selectedProfileCode;
        }

        if (linkedProvider == null) {
            return null;
        }

        return StringUtils.trimToNull(linkedProvider.getProfessionalProfileCode());
    }

    private Provider findLinkedActiveProvider(String linkedProviderPersonId) {
        String normalizedPersonId = StringUtils.trimToNull(linkedProviderPersonId);
        if (normalizedPersonId == null) {
            return null;
        }

        return providerService.getAllActiveProviders().stream()
                .filter(provider -> provider.getPerson() != null)
                .filter(provider -> normalizedPersonId.equals(provider.getPerson().getId()))
                .findFirst()
                .orElse(null);
    }

    private void disableNavigationButtons(HttpServletRequest request) {
        request.setAttribute(PREVIOUS_DISABLED, TRUE);
        request.setAttribute(NEXT_DISABLED, TRUE);
    }

    private void saveUserLabUnitRoles(SystemUser user, UnifiedSystemUserForm form, String loggedOnUserId,
            List<String> selectedCustomRoleIds, List<String> derivedCustomPermissionRoleIds) {
        Map<String, Set<String>> selectedLabUnitRolesMap = new HashMap<>(form.getSelectedTestSectionLabUnits());
        mergeCustomLabRoles(selectedLabUnitRolesMap, selectedCustomRoleIds, derivedCustomPermissionRoleIds);

        userService.saveUserLabUnitRoles(user, selectedLabUnitRolesMap, loggedOnUserId);
    }

    private void mergeCustomLabRoles(Map<String, Set<String>> selectedLabUnitRolesMap, List<String> selectedCustomRoleIds,
            List<String> permissionRoleIds) {
        List<String> labRoleIds = getLabUnitRoleIds(permissionRoleIds);
        if (labRoleIds.isEmpty()) {
            return;
        }

        Map<String, List<String>> customRoleLabUnitScopes = customRoleDefinitionService
                .getApplicableLabUnitIdsForCustomRoles(selectedCustomRoleIds);
        Map<String, List<String>> customRolePermissionMap = customRoleDefinitionService
                .getPermissionRoleIdsForCustomRoles(selectedCustomRoleIds);

        selectedCustomRoleIds.forEach(customRoleId -> {
            List<String> scopedLabUnits = customRoleLabUnitScopes.getOrDefault(customRoleId, List.of());
            List<String> scopedLabRoleIds = getLabUnitRoleIds(
                    customRolePermissionMap.getOrDefault(customRoleId, List.of()));
            if (scopedLabRoleIds.isEmpty()) {
                return;
            }
            if (scopedLabUnits.isEmpty()) {
                scopedLabUnits = List.of(ALL_LAB_UNITS);
            }
            scopedLabUnits.forEach(labUnitId -> {
                Set<String> currentRoleIds = new HashSet<>(
                        selectedLabUnitRolesMap.getOrDefault(labUnitId, new HashSet<>()));
                currentRoleIds.addAll(scopedLabRoleIds);
                selectedLabUnitRolesMap.put(labUnitId, currentRoleIds);
            });
        });
    }

    private List<String> getGlobalRoleIds(List<String> roleIds) {
        String globalParentRoleId = roleService.getRoleByName(Constants.GLOBAL_ROLES_GROUP).getId();
        return normalizedRoleIds(roleIds).stream().map(roleService::getRoleById).filter(Objects::nonNull)
                .filter(role -> globalParentRoleId.equals(role.getGroupingParent())).map(Role::getId).toList();
    }

    private List<String> getLabUnitRoleIds(List<String> roleIds) {
        String labUnitParentRoleId = roleService.getRoleByName(Constants.LAB_ROLES_GROUP).getId();
        return normalizedRoleIds(roleIds).stream().map(roleService::getRoleById).filter(Objects::nonNull)
                .filter(role -> labUnitParentRoleId.equals(role.getGroupingParent())).map(Role::getId).toList();
    }

    private List<String> normalizedRoleIds(List<String> roleIds) {
        if (roleIds == null) {
            return new ArrayList<>();
        }
        return roleIds.stream().filter(StringUtils::isNotBlank).map(StringUtils::trim).collect(Collectors.toList());
    }

    private List<String> deduplicateRoleIds(List<String> roleIds) {
        return new ArrayList<>(new LinkedHashSet<>(normalizedRoleIds(roleIds)));
    }

    private void setLabunitRolesForExistingUser(UnifiedSystemUserForm form) {
        UserLabUnitRoles roles = userService.getUserLabUnitRoles(form.getSystemUserId());
        if (roles != null) {
            Set<LabUnitRoleMap> roleMaps = roles.getLabUnitRoleMap();
            List<String> userLabUnits = new ArrayList<>();
            roleMaps.forEach(map -> userLabUnits.add(map.getLabUnit()));
            JSONObject userLabData = new JSONObject();
            if (userLabUnits.contains(ALL_LAB_UNITS)) {
                roleMaps.stream().filter(map -> map.getLabUnit().equals(ALL_LAB_UNITS)).forEach(
                        map -> userLabData.put(map.getLabUnit(), map.getRoles().stream().collect(Collectors.toList())));
            } else {
                for (LabUnitRoleMap map : roleMaps) {
                    userLabData.put(map.getLabUnit(), map.getRoles().stream().collect(Collectors.toList()));
                }
            }
            form.setUserLabRoleData(userLabData);

            Map<String, Set<String>> userTestSectionLabUnits = new HashMap<>();
            if (userLabUnits.contains(ALL_LAB_UNITS)) {
                roleMaps.stream().filter(map -> map.getLabUnit().equals(ALL_LAB_UNITS))
                        .forEach(map -> userTestSectionLabUnits.put(map.getLabUnit(), new HashSet<>(map.getRoles())));
            } else {
                for (LabUnitRoleMap map : roleMaps) {
                    userTestSectionLabUnits.put(testSectionService.get(map.getLabUnit()).getId(),
                            new HashSet<>(map.getRoles().stream().map(r -> roleService.getRoleById(r).getId().trim())
                                    .collect(Collectors.toList())));
                }
            }

            form.setSelectedTestSectionLabUnits(userTestSectionLabUnits);
        }
    }

    private JSONArray getDisplaySystemUsersJsonArray() {
        JSONArray displayUsers = new JSONArray();
        systemUserService.getAll().stream().filter(user -> user.getIsActive().equals(YES))
                .map(user -> new JSONObject()
                        .put("label", user.getLoginName() + " | " + user.getFirstName() + " " + user.getLastName())
                        .put("value", user.getId()))
                .forEach(userJson -> displayUsers.put(userJson));
        return displayUsers;
    }

    @Override
    protected String findLocalForward(String forward) {
        if (FWD_SUCCESS.equals(forward)) {
            return "unifiedSystemUserDefinition";
        } else if (FWD_FAIL.equals(forward)) {
            return "redirect:/MasterListsPage";
        } else if (FWD_SUCCESS_INSERT.equals(forward)) {
            return "redirect:/UnifiedSystemUser";
        } else if (FWD_FAIL_INSERT.equals(forward)) {
            return "unifiedSystemUserDefinition";
        } else {
            return "PageNotFound";
        }
    }

    @Override
    protected String getPageTitleKey() {
        String id = request.getParameter(ID);
        boolean isNew = GenericValidator.isBlankOrNull(id) || "0".equals(id);
        return isNew ? "unifiedSystemUser.add.title" : "unifiedSystemUser.edit.title";
    }

    @Override
    protected String getPageSubtitleKey() {
        String id = request.getParameter(ID);
        boolean isNew = GenericValidator.isBlankOrNull(id) || "0".equals(id);
        return isNew ? "unifiedSystemUser.add.title" : "unifiedSystemUser.edit.title";
    }
}
