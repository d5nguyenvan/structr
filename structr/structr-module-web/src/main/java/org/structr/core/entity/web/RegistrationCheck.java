/*
 *  Copyright (C) 2011 Axel Morgner, structr <structr@structr.org>
 * 
 *  This file is part of structr <http://structr.org>.
 * 
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.entity.web;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.jsoup.Jsoup;
import org.structr.common.CurrentRequest;
import org.structr.common.MailHelper;
import org.structr.common.RelType;
import org.structr.core.Command;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Folder;
import org.structr.core.entity.Person;
import org.structr.core.entity.SuperUser;
import org.structr.core.entity.User;
import org.structr.core.node.CreateNodeCommand;
import org.structr.core.node.CreateRelationshipCommand;
import org.structr.core.node.FindUserCommand;
import org.structr.core.node.IndexNodeCommand;
import org.structr.core.node.NodeAttribute;
import org.structr.core.node.StructrTransaction;
import org.structr.core.node.TransactionCommand;
import org.structr.core.node.search.Search;
import org.structr.core.node.search.SearchAttribute;
import org.structr.core.node.search.SearchNodeCommand;

/**
 * Checks registration information.
 *
 * <ul>
 * <li>All mandatory fields must be filled out
 * <li>Username must not exist
 * <li>Passwords must match
 * <li>Password must be strong enough
 * <li>Checkbox for agreement with terms of use has to be checked
 * </ul>
 *
 * If anything is ok, the user will be registered.
 *
 * If not, error messages are displayed.
 *
 * @author axel
 */
public class RegistrationCheck extends LoginCheck {

    private static final Logger logger = Logger.getLogger(RegistrationCheck.class.getName());
    private final static String ICON_SRC = "/images/application_key.png";

    @Override
    public String getIconSrc() {
        return ICON_SRC;
    }
    private final static String HTML_HEADER = "<html>";
    private final static String HTML_FOOTER = "</html>";
    private final static String NUMBER_OF_REGISTRATION_ATTEMPTS = "numberOfRegistrationAttempts";
    private final static String REGISTRATION_FAILURE_USER_EXISTS = "A user with this username already exists, please choose another username!";
    private final static String REGISTRATION_FAILURE_PASSWORDS_DONT_MATCH = "The passwords don't match!";
    private final static String REGISTRATION_FAILURE_EMAILS_DONT_MATCH = "The email adresses don't match!";
    private final static String REGISTRATION_FAILURE_MANDATORY_FIELD_EMPTY = "Please fill out all mandatory fields!";
    private final static String REGISTRATION_FAILURE_NOT_AGREED_TO_TERMS_OF_USE = "To register, you have to agree to the terms of use!";
    private final static String REGISTRATION_FAILURE_WEAK_PASSWORD = "Your password is too weak, it must have at least 6 characters, contain at least one digit, one upper case and one lower case character.";
    private final static String REGISTRATION_FAILURE_INVALID_DATE = "Invalid date format";
    public final static String PUBLIC_USER_DIRECTORY_NAME_KEY = "publicUserDirectoryName";
    public final static String SENDER_ADDRESS_KEY = "senderAddress";
    public final static String SENDER_NAME_KEY = "senderName";
    public final static String INLINE_CSS_KEY = "inlineCss";
    public final static String ASSIGNED_USERNAME_KEY = "assignedUsername";
    public final static String CONFIRMATION_KEY_FIELD_NAME_KEY = "confirmationKeyFieldName";
    public final static String CONFIRM_PASSWORD_FIELD_NAME_KEY = "confirmPasswordFieldName";
    public final static String AGREED_TO_TERMS_OF_USE_FIELD_NAME_KEY = "agreedToTermsOfUseFieldName";
    public final static String NEWSLETTER_FIELD_NAME_KEY = "newsletterFieldName";
    private List<String> errors = new LinkedList<String>();

    /**
     * Return name of confirmation key field
     *
     * @return
     */
    public String getConfirmationKeyFieldName() {
        return getStringProperty(CONFIRMATION_KEY_FIELD_NAME_KEY);
    }

    /**
     * Set name of confirmation key field
     *
     * @param value
     */
    public void setConfirmationKeyFieldName(final String value) {
        setProperty(CONFIRMATION_KEY_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of confirm password field
     *
     * @return
     */
    public String getConfirmPasswordFieldName() {
        return getStringProperty(CONFIRM_PASSWORD_FIELD_NAME_KEY);
    }

    /**
     * Set name of confirm password field
     *
     * @param value
     */
    public void setConfirmPasswordFieldName(final String value) {
        setProperty(CONFIRM_PASSWORD_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of first name field
     *
     * @return
     */
    public String getFirstNameFieldName() {
        return getStringProperty(RegistrationForm.FIRST_NAME_FIELD_NAME_KEY);
    }

    /**
     * Set name of username field
     *
     * @param value
     */
    public void setFirstNameFieldName(final String value) {
        setProperty(RegistrationForm.FIRST_NAME_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of last name field
     *
     * @return
     */
    public String getLastNameFieldName() {
        return getStringProperty(RegistrationForm.LAST_NAME_FIELD_NAME_KEY);
    }

    /**
     * Set name of last name field
     *
     * @param value
     */
    public void setLastNameFieldName(final String value) {
        setProperty(RegistrationForm.LAST_NAME_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of email field
     *
     * @return
     */
    public String getEmailFieldName() {
        return getStringProperty(RegistrationForm.EMAIL_FIELD_NAME_KEY);
    }

    /**
     * Set name of email field
     *
     * @param value
     */
    public void setEmailFieldName(final String value) {
        setProperty(RegistrationForm.EMAIL_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of confirm email field
     *
     * @return
     */
    public String getConfirmEmailFieldName() {
        return getStringProperty(RegistrationForm.CONFIRM_EMAIL_FIELD_NAME_KEY);
    }

    /**
     * Set name of confirm email field
     *
     * @param value
     */
    public void setConfirmEmailFieldName(final String value) {
        setProperty(RegistrationForm.CONFIRM_EMAIL_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of zip code field
     *
     * @return
     */
    public String getZipCodeFieldName() {
        return getStringProperty(RegistrationForm.ZIP_CODE_FIELD_NAME_KEY);
    }

    /**
     * Set name of zip code field
     *
     * @param value
     */
    public void setZipCodeFieldName(final String value) {
        setProperty(RegistrationForm.ZIP_CODE_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of city field
     *
     * @return
     */
    public String getCityFieldName() {
        return getStringProperty(RegistrationForm.CITY_FIELD_NAME_KEY);
    }

    /**
     * Set name of city field
     *
     * @param value
     */
    public void setCityFieldName(final String value) {
        setProperty(RegistrationForm.CITY_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of street field
     *
     * @return
     */
    public String getStreetFieldName() {
        return getStringProperty(RegistrationForm.STREET_FIELD_NAME_KEY);
    }

    /**
     * Set name of street field
     *
     * @param value
     */
    public void setStreetFieldName(final String value) {
        setProperty(RegistrationForm.STREET_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of state field
     *
     * @return
     */
    public String getStateFieldName() {
        return getStringProperty(RegistrationForm.STATE_FIELD_NAME_KEY);
    }

    /**
     * Set name of state field
     *
     * @param value
     */
    public void setStateFieldName(final String value) {
        setProperty(RegistrationForm.STATE_FIELD_NAME_KEY, value);
    }


    /**
     * Return name of country field
     *
     * @return
     */
    public String getCountryFieldName() {
        return getStringProperty(RegistrationForm.COUNTRY_FIELD_NAME_KEY);
    }

    /**
     * Set name of country field
     *
     * @param value
     */
    public void setCountryFieldName(final String value) {
        setProperty(RegistrationForm.COUNTRY_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of birthday field
     *
     * @return
     */
    public String getBirthdayFieldName() {
        return getStringProperty(RegistrationForm.BIRTHDAY_FIELD_NAME_KEY);
    }

    /**
     * Set name of birthday field
     *
     * @param value
     */
    public void setBirthdayFieldName(final String value) {
        setProperty(RegistrationForm.BIRTHDAY_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of gender field
     *
     * @return
     */
    public String getGenderFieldName() {
        return getStringProperty(RegistrationForm.GENDER_FIELD_NAME_KEY);
    }

    /**
     * Set name of gender field
     *
     * @param value
     */
    public void setGenderFieldName(final String value) {
        setProperty(RegistrationForm.GENDER_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of agreed to terms of use field
     *
     * @return
     */
    public String getAgreedToTermsOfUseFieldName() {
        return getStringProperty(AGREED_TO_TERMS_OF_USE_FIELD_NAME_KEY);
    }

    /**
     * Set name of agreed to terms of use field
     *
     * @param value
     */
    public void setAgreedToTermsOfUseFieldName(final String value) {
        setProperty(AGREED_TO_TERMS_OF_USE_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of newsletter field
     *
     * @return
     */
    public String getNewsletterFieldName() {
        return getStringProperty(NEWSLETTER_FIELD_NAME_KEY);
    }

    /**
     * Set name of newsletter field
     *
     * @param value
     */
    public void setNewsletterFieldName(final String value) {
        setProperty(NEWSLETTER_FIELD_NAME_KEY, value);
    }

    /**
     * Return name of sender address field
     *
     * @return
     */
    public String getSenderAddress() {
        return getStringProperty(SENDER_ADDRESS_KEY);
    }

    /**
     * Set name of sender address field
     *
     * @param value
     */
    public void setSenderAddress(final String value) {
        setProperty(SENDER_ADDRESS_KEY, value);
    }

    /**
     * Return name of sender name field
     *
     * @return
     */
    public String getSenderName() {
        return getStringProperty(SENDER_NAME_KEY);
    }

    /**
     * Set name of sender name field
     *
     * @param value
     */
    public void setSenderName(final String value) {
        setProperty(SENDER_NAME_KEY, value);
    }

    /**
     * Return name of public user directory
     *
     * @return
     */
    public String getPublicUserDirectoryName() {
        return getStringProperty(PUBLIC_USER_DIRECTORY_NAME_KEY);
    }

    /**
     * Set name of public user directory
     *
     * @param value
     */
    public void setPublicUserDirectoryName(final String value) {
        setProperty(PUBLIC_USER_DIRECTORY_NAME_KEY, value);
    }

    /**
     * Return inline CSS
     *
     * @return
     */
    public String getInlineCss() {
        return getStringProperty(INLINE_CSS_KEY);
    }

    /**
     * Set inline CSS
     *
     * @param value
     */
    public void setInlineCss(final String value) {
        setProperty(INLINE_CSS_KEY, value);
    }

    /**
     * Return assigned username
     *
     * @return
     */
    public String getAssignedUsername() {
        return getStringProperty(ASSIGNED_USERNAME_KEY);
    }

    /**
     * Set assigned username
     *
     * @param value
     */
    public void setAssignedUsername(final String value) {
        setProperty(ASSIGNED_USERNAME_KEY, value);
    }

    /**
     * Render view
     *
     * @param out
     * @param startNode
     * @param editUrl
     * @param editNodeId
     */
    @Override
    public void renderView(StringBuilder out, final AbstractNode startNode,
            final String editUrl, final Long editNodeId) {

        // if this page is requested to be edited, render edit frame
        if (editNodeId != null && getId() == editNodeId.longValue()) {

            renderEditFrame(out, editUrl);

            // otherwise, render subnodes in edit mode
        } else {

            HttpServletRequest request = CurrentRequest.getRequest();

            if (request == null) {
                return;
            }

            HttpSession session = request.getSession();

            if (session == null) {
                return;
            }

            String usernameFromSession = (String) session.getAttribute(USERNAME_KEY);
//            String usernameFromSession = CurrentSession.getGlobalUsername();
            Boolean alreadyLoggedIn = usernameFromSession != null;

            if (alreadyLoggedIn) {
                return;
            }

            Boolean sessionBlocked = (Boolean) session.getAttribute(SESSION_BLOCKED);

            if (Boolean.TRUE.equals(sessionBlocked)) {
                out.append("<div class=\"errorMsg\">").append("Unable to register. Session is blocked.").append("</div>");
                return;
            }

            // Get values from config page, or defaults

            String usernameFieldName = getUsernameFieldName() != null ? getUsernameFieldName() : RegistrationForm.defaultUsernameFieldName;
            String passwordFieldName = getPasswordFieldName() != null ? getPasswordFieldName() : RegistrationForm.defaultPasswordFieldName;
            String confirmPasswordFieldName = getConfirmPasswordFieldName() != null ? getConfirmPasswordFieldName() : RegistrationForm.defaultConfirmPasswordFieldName;
            String firstNameFieldName = getFirstNameFieldName() != null ? getFirstNameFieldName() : RegistrationForm.defaultFirstNameFieldName;
            String lastNameFieldName = getLastNameFieldName() != null ? getLastNameFieldName() : RegistrationForm.defaultLastNameFieldName;
            String emailFieldName = getEmailFieldName() != null ? getEmailFieldName() : RegistrationForm.defaultEmailFieldName;
            String confirmEmailFieldName = getConfirmEmailFieldName() != null ? getConfirmEmailFieldName() : RegistrationForm.defaultConfirmEmailFieldName;
            String streetFieldName = getStreetFieldName() != null ? getStreetFieldName() : RegistrationForm.defaultStreetFieldName;
            String zipCodeFieldName = getZipCodeFieldName() != null ? getZipCodeFieldName() : RegistrationForm.defaultZipCodeFieldName;
            String cityFieldName = getCityFieldName() != null ? getCityFieldName() : RegistrationForm.defaultCityFieldName;
            String countryFieldName = getCountryFieldName() != null ? getCountryFieldName() : RegistrationForm.defaultCountryFieldName;
            String stateFieldName = getStateFieldName() != null ? getStateFieldName() : RegistrationForm.defaultStateFieldName;
            String birthdayFieldName = getBirthdayFieldName() != null ? getBirthdayFieldName() : RegistrationForm.defaultBirthdayFieldName;
            String genderFieldName = getGenderFieldName() != null ? getGenderFieldName() : RegistrationForm.defaultGenderFieldName;

            String agreedToTermsOfUseFieldName = getAgreedToTermsOfUseFieldName() != null ? getAgreedToTermsOfUseFieldName() : RegistrationForm.defaultAgreedToTermsOfUseFieldName;
            String newsletterFieldName = getNewsletterFieldName() != null ? getNewsletterFieldName() : RegistrationForm.defaultNewsletterFieldName;
            String confirmationKeyFieldName = getConfirmationKeyFieldName() != null ? getConfirmationKeyFieldName() : RegistrationForm.defaultConfirmationKeyFieldName;
            String submitButtonName = getSubmitButtonName() != null ? getSubmitButtonName() : RegistrationForm.defaultSubmitButtonName;
            String antiRobotFieldName = getAntiRobotFieldName() != null ? getAntiRobotFieldName() : RegistrationForm.defaultAntiRobotFieldName;

            final String publicUserDirectoryName = getPublicUserDirectoryName() != null ? getPublicUserDirectoryName() : RegistrationForm.defaultPublicUserDirectoryName;
            String senderAddress = getSenderAddress() != null ? getSenderAddress() : RegistrationForm.defaultSenderAddress;
            String senderName = getSenderName() != null ? getSenderName() : RegistrationForm.defaultSenderName;
            String inlineCss = getInlineCss() != null ? getInlineCss() : RegistrationForm.defaultInlineCss;
            final String assignedUsername = getAssignedUsername() != null ? getAssignedUsername() : RegistrationForm.defaultAssignedUsername;

            final String username = request.getParameter(usernameFieldName);
            final String password = request.getParameter(passwordFieldName);
            String confirmPassword = request.getParameter(confirmPasswordFieldName);
            final String firstName = request.getParameter(firstNameFieldName);
            final String lastName = request.getParameter(lastNameFieldName);
            final String email = request.getParameter(emailFieldName);
            String confirmEmail = request.getParameter(confirmEmailFieldName);
            final String street = request.getParameter(streetFieldName);
            final String zipCode = request.getParameter(zipCodeFieldName);
            final String city = request.getParameter(cityFieldName);
            final String state = request.getParameter(stateFieldName);
            final String country = request.getParameter(countryFieldName);
            final String birthday = request.getParameter(birthdayFieldName);
            final String gender = request.getParameter(genderFieldName);
            String agreedToTermsOfUse = request.getParameter(agreedToTermsOfUseFieldName);
            final String newsletter = request.getParameter(newsletterFieldName);
            String confirmationKey = request.getParameter(confirmationKeyFieldName);
            String submitButton = request.getParameter(submitButtonName);
            String antiRobot = request.getParameter(antiRobotFieldName);

            int maxRetries = getMaxErrors() > 0 ? getMaxErrors() : defaultMaxErrors;
            int delayThreshold = getDelayThreshold() > 0 ? getDelayThreshold() : defaultDelayThreshold;
            int delayTime = getDelayTime() > 0 ? getDelayTime() : defaultDelayTime;

            if (StringUtils.isNotEmpty(antiRobot)) {
                // Don't process form if someone has filled the anti-robot field
                return;
            }

            // Check if we have a user with this name
            User loginUser = null;

            // Check confirmation key
            if (StringUtils.isNotEmpty(confirmationKey) && StringUtils.isNotEmpty(username)) {

                loginUser = (User) Services.command(FindUserCommand.class).execute(username);

                if (loginUser == null) {
                    String message = "<div class=\"errorMsg\">Missing username or user not found!</div>";
                    registerFailure(out, message, session, maxRetries, delayThreshold, delayTime);
                }

                if (hasFailures(out)) {
                    return;
                }

                String confirmationKeyFromUser = loginUser.getConfirmationKey();
                if (StringUtils.isNotBlank(confirmationKeyFromUser) && confirmationKeyFromUser.equals(confirmationKey)) {

                    loginUser.setBlocked(false);
                    loginUser.setConfirmationKey(null);
                    out.append("<div class=\"okMsg\">").append("Registration complete, you may now login.").append("</div>");
                    return;
                }


            }

            if (StringUtils.isEmpty(submitButton)) {
                // Don't process form if submit button was not pressed
                return;
            }

//            if (StringUtils.isEmpty(username)) {
//                String message = "<div class=\"errorMsg\">Plesae choose a username!</div>";
//                registerFailure(out, message, session, maxRetries, delayThreshold, delayTime);
//            } else {
//                loginUser = (User) Services.command(FindUserCommand.class).execute(username);
//            }

            if (StringUtils.isNotEmpty(username)) {
                loginUser = (User) Services.command(FindUserCommand.class).execute(username);
            }

            if (loginUser != null) {
                logger.log(Level.INFO, "User with name {0} already exists", loginUser);
                String message = "<div class=\"errorMsg\">" + REGISTRATION_FAILURE_USER_EXISTS + "</div>";
                registerFailure(out, message, session, maxRetries, delayThreshold, delayTime);
            }

            if (hasFailures(out)) {
                return;
            }

            // Check mandatory fields

            boolean usernameEmpty = StringUtils.isEmpty(username);
            boolean passwordEmpty = StringUtils.isEmpty(password);
            boolean confirmPasswordEmpty = StringUtils.isEmpty(confirmPassword);
            boolean firstNameEmpty = StringUtils.isEmpty(firstName);
            boolean lastNameEmpty = StringUtils.isEmpty(lastName);
            boolean emailEmpty = StringUtils.isEmpty(email);
            boolean confirmEmailEmpty = StringUtils.isEmpty(confirmEmail);

            if (firstNameEmpty || lastNameEmpty || emailEmpty || confirmEmailEmpty) {

                StringBuilder errorsOnMandatoryFields = new StringBuilder();

                errorsOnMandatoryFields.append("<div class=\"errorMsg\">").append(REGISTRATION_FAILURE_MANDATORY_FIELD_EMPTY).append("</div>");
                errorsOnMandatoryFields.append("<style type=\"text/css\">");

                if (usernameEmpty) {
                    errorsOnMandatoryFields.append("input[name=").append(usernameFieldName).append("] { background-color: #ffc }");
                } else {
                    loginUser = (User) Services.command(FindUserCommand.class).execute(username);

                    if (loginUser != null) {
                        logger.log(Level.FINE, "User with name {0} already exists", loginUser);
                        String message = "<div class=\"errorMsg\">" + REGISTRATION_FAILURE_USER_EXISTS + "</div>";
                        registerFailure(out, message, session, maxRetries, delayThreshold, delayTime);
                    }

                }
                if (passwordEmpty) {
                    errorsOnMandatoryFields.append("input[name=").append(passwordFieldName).append("] { background-color: #ffc }");
                }
                if (confirmPasswordEmpty) {
                    errorsOnMandatoryFields.append("input[name=").append(confirmPasswordFieldName).append("] { background-color: #ffc }");
                }
                if (firstNameEmpty) {
                    errorsOnMandatoryFields.append("input[name=").append(firstNameFieldName).append("] { background-color: #ffc }");
                }
                if (firstNameEmpty) {
                    errorsOnMandatoryFields.append("input[name=").append(firstNameFieldName).append("] { background-color: #ffc }");
                }
                if (lastNameEmpty) {
                    errorsOnMandatoryFields.append("input[name=").append(lastNameFieldName).append("] { background-color: #ffc }");
                }
                if (emailEmpty) {
                    errorsOnMandatoryFields.append("input[name=").append(emailFieldName).append("] { background-color: #ffc }");
                }
                if (confirmEmailEmpty) {
                    errorsOnMandatoryFields.append("input[name=").append(confirmEmailFieldName).append("] { background-color: #ffc }");
                }

                errorsOnMandatoryFields.append("</style>");

                registerFailure(out, errorsOnMandatoryFields.toString(), session, maxRetries, delayThreshold, delayTime);
            }

            if (hasFailures(out)) {
                return;
            }

            if (!(password.equals(confirmPassword))) {
                String message = "<div class=\"errorMsg\">" + REGISTRATION_FAILURE_PASSWORDS_DONT_MATCH + "</div>";
                registerFailure(out, message, session, maxRetries, delayThreshold, delayTime);
            }

            Date parsedDate = null;
            String parseErrorMsg = null;
            try {
                parsedDate = DateUtils.parseDate(birthday, new String[]{"MM/dd/yyyy"});
            } catch (Exception e) {
                parseErrorMsg = e.getLocalizedMessage();
            }

            if (parsedDate == null) {
                String message = "<div class=\"errorMsg\">" + REGISTRATION_FAILURE_INVALID_DATE + ": " + parseErrorMsg + "</div>";
                registerFailure(out, message, session, maxRetries, delayThreshold, delayTime);
            }

            // Check that password is strong enough:
            // Must contain at least one digit, one upper and one lower case character.
            // Length must be greater than 6.

            String pattern = "((?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).{6,})";

            if (!(password.matches(pattern))) {
                String message = "<div class=\"errorMsg\">" + REGISTRATION_FAILURE_WEAK_PASSWORD + "</div>";
                registerFailure(out, message, session, maxRetries, delayThreshold, delayTime);
            }

            if (!(email.equals(confirmEmail))) {
                String message = "<div class=\"errorMsg\">" + REGISTRATION_FAILURE_EMAILS_DONT_MATCH + "</div>";
                registerFailure(out, message, session, maxRetries, delayThreshold, delayTime);
            }

            // Here we have all mandatory fields

            // Check that user has agreed to the terms of use
            if (StringUtils.isEmpty(agreedToTermsOfUse)) {
                String message = "<div class=\"errorMsg\">" + REGISTRATION_FAILURE_NOT_AGREED_TO_TERMS_OF_USE + "</div>";
                registerFailure(out, message, session, maxRetries, delayThreshold, delayTime);
            }


            // Check e-mail by sending a confirmation key to the given e-mail address

//            if (user.length() > 0 && password.length() > 0) {
//                email.setAuthentication(user, password);
//            }
            StringBuilder content = new StringBuilder(HTML_HEADER);

//            content.append("<head>").append(inlineCss).append("</head><body>");
            content.append(inlineCss);

            content.append("<p>An account was requested with the following personal data:</p>");

            content.append("<table>");

            if (StringUtils.isNotEmpty(username)) {
                content.append("<tr><td>Username:</td><td>").append(username).append("</td></tr>");
            }
            if (StringUtils.isNotEmpty(firstName)) {
                content.append("<tr><td>First Name:</td><td>").append(firstName).append("</td></tr>");
            }
            if (StringUtils.isNotEmpty(lastName)) {
                content.append("<tr><td>Last Name:</td><td>").append(lastName).append("</td></tr>");
            }
            if (StringUtils.isNotEmpty(email)) {
                content.append("<tr><td>E-Mail Address:</td><td>").append(email).append("</td></tr>");
            }
            if (StringUtils.isNotEmpty(street)) {
                content.append("<tr><td>Street:</td><td>").append(street).append("</td></tr>");
            }
            if (StringUtils.isNotEmpty(zipCode)) {
                content.append("<tr><td>ZIP Code:</td><td>").append(zipCode).append("</td></tr>");
            }
            if (StringUtils.isNotEmpty(city)) {
                content.append("<tr><td>City:</td><td>").append(city).append("</td></tr>");
            }
            if (StringUtils.isNotEmpty(country)) {
                content.append("<tr><td>Country:</td><td>").append(country).append("</td></tr>");
            }
            if (StringUtils.isNotEmpty(newsletter)) {
                content.append("<tr><td>Newsletter:</td><td>yes").append("</td></tr>");
            }
            content.append("</table>");

            if (hasFailures(out)) {
                return;
            }

            // Create hash digest from user name as confirmation key
            final String confirmationKeyForMail = DigestUtils.sha256Hex(username);

//            content.append("<p>Your confirmation key is ").append(confirmationKeyForMail).append("</p>");
//            content.append("<p>Please click on the link below to complete your registration.</p>");
            content.append("<p><a href=\"");

            content.append(request.getScheme()).append("://").append(request.getServerName()).append(":").append(request.getServerPort());

            String pageUrl = request.getContextPath().concat(
                    "/view".concat(startNode.getNodePath().replace("&", "%26"))).concat("?").concat(confirmationKeyFieldName).concat("=").concat(confirmationKeyForMail).concat("&").concat(usernameFieldName).concat("=").concat(username);

            content.append(pageUrl);
            content.append("\">");
            content.append("Please click on this link to complete your registration.");
            content.append("</a></p>");

            content.append("<p>If you think that this registration request was not legitimate, please forward this message to ").append(senderAddress).append(".</p>");
            content.append("<p>If something is not working as expected, please forward this message to ").append(senderAddress).append(", together with a short description of the issue you had. Thank you.</p>");

//            content.append("</body>");

            content.append(HTML_FOOTER);

            String to = email;
            String toName = firstName + " " + lastName;
            String from = senderAddress;
            String fromName = senderName;
            String subject = "Account requested, please confirm your e-mail address";
            String htmlContent = content.toString();
            String textContent = Jsoup.parse(htmlContent).text();

            try {

                MailHelper.sendHtmlMail(from, fromName, to, toName, null, null, senderAddress, subject, htmlContent, textContent);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error while sending registration e-mail", e);
                String message = "<div class=\"errorMsg\">Error while sending registration e-mail: " + e.getLocalizedMessage() + "</div>";
                registerFailure(out, message, session, maxRetries, delayThreshold, delayTime);
            }

            if (hasFailures(out)) {
                return;
            }

            final RegistrationCheck registrationCheckNode = this;
            final Date birthdayDate = parsedDate;

            // Create new user (will reserve username)
            User newUser = (User) Services.command(TransactionCommand.class).execute(new StructrTransaction() {

                @Override
                public Object execute() {

                    Command create = Services.command(CreateNodeCommand.class);
                    Command link = Services.command(CreateRelationshipCommand.class);
                    Command search = Services.command(SearchNodeCommand.class);

                    List<SearchAttribute> searchAttrs = new LinkedList<SearchAttribute>();

                    // Get user to assign the nodes to be created to
                    User assignedUser = null;
                    searchAttrs.add(Search.andExactName(assignedUsername));
                    searchAttrs.add(Search.andExactType(User.class.getSimpleName()));

                    List<User> userList = (List<User>) search.execute(new SuperUser(), null, false, false, searchAttrs);
                    if (!(userList.isEmpty())) {
                        assignedUser = userList.get(0);
                    }

                    // Clear search attributes to be reusable
                    searchAttrs.clear();

                    User newUser = (User) create.execute(assignedUser,
                            new NodeAttribute(AbstractNode.NAME_KEY, username),
                            new NodeAttribute(AbstractNode.TYPE_KEY, User.class.getSimpleName()),
                            new NodeAttribute(Person.FIRST_NAME_KEY, firstName),
                            new NodeAttribute(Person.LAST_NAME_KEY, lastName),
                            new NodeAttribute(User.REAL_NAME_KEY, (firstName + " " + lastName)),
                            new NodeAttribute(Person.STREET_KEY, street),
                            new NodeAttribute(Person.ZIP_CODE_KEY, zipCode),
                            new NodeAttribute(Person.CITY_KEY, city),
                            new NodeAttribute(Person.STATE_KEY, state),
                            new NodeAttribute(Person.COUNTRY_KEY, country),
                            new NodeAttribute(Person.GENDER_KEY, gender),
                            new NodeAttribute(Person.BIRTHDAY_KEY, birthdayDate),
                            new NodeAttribute(Person.EMAIL_1_KEY, email),
                            new NodeAttribute(Person.NEWSLETTER_KEY, StringUtils.isNotEmpty(newsletter)),
                            new NodeAttribute(User.BLOCKED_KEY, true),
                            new NodeAttribute(User.CONFIRMATION_KEY_KEY, confirmationKeyForMail));

                    // Use method for password to be hashed
                    newUser.setPassword(password);

                    searchAttrs.add(Search.andExactName(publicUserDirectoryName));
                    searchAttrs.add(Search.andExactType(Folder.class.getSimpleName()));

                    // Look for existing public user directory
                    List<Folder> folders = (List<Folder>) search.execute(
                            null, registrationCheckNode, false, false, searchAttrs);

                    Folder publicUserDirectory = null;

                    if (folders.size() > 0) {
                        publicUserDirectory = folders.get(0);
                    }

                    if (assignedUser == null) {
                        logger.log(Level.WARNING, "No assignable user, all objects created will only be visible to the super user!");
                    }

                    // If no public user directory exists, create one and link to this node
                    if (publicUserDirectory == null) {
                        publicUserDirectory = (Folder) create.execute(assignedUser,
                                new NodeAttribute(AbstractNode.NAME_KEY, publicUserDirectoryName),
                                new NodeAttribute(AbstractNode.TYPE_KEY, Folder.class.getSimpleName()));

                        // Link to registration node
                        link.execute(registrationCheckNode, publicUserDirectory, RelType.HAS_CHILD);

                    }

                    link.execute(publicUserDirectory, newUser, RelType.HAS_CHILD);

                    // Index user to be findable
                    Services.command(IndexNodeCommand.class).execute(newUser);

                    return newUser;
                }
            });

            logger.log(Level.INFO, "A new public user {0}[{1}] has been created.", new Object[]{newUser.getName(), newUser.getId()});

            // Clear all blocking stuff
            session.removeAttribute(SESSION_BLOCKED);
            session.removeAttribute(NUMBER_OF_REGISTRATION_ATTEMPTS);

            out.append("<div class=\"okMsg\">").append("An e-mail has been sent to you to validate the given e-mail address. Please click on the link in the e-mail to complete registration.").append("</div>");

        }
    }

    private boolean hasFailures(final StringBuilder out) {
        if (!(errors.isEmpty())) {
            for (String error : errors) {
                out.append(error);
            }
            return true;
        }
        return false;
    }

    private void registerFailure(final StringBuilder out, final String errorMsg, final HttpSession session, final int maxRetries, final int delayThreshold, final int delayTime) {

        errors.add(errorMsg);

        Integer retries = (Integer) session.getAttribute(NUMBER_OF_REGISTRATION_ATTEMPTS);

        if (retries != null && retries > maxRetries) {

            logger.log(Level.SEVERE, "More than {0} registration failures, session {1} is blocked", new Object[]{maxRetries, session.getId()});
            session.setAttribute(SESSION_BLOCKED, true);
            String message = "<div class=\"errorMsg\">Too many unsuccessful registration attempts, your session is blocked for registration!</div>";
            errors.add(message);

        } else if (retries != null && retries > delayThreshold) {

            logger.log(Level.INFO, "More than {0} registration failures, execution delayed by {1} seconds", new Object[]{delayThreshold, delayTime});

            try {
                Thread.sleep(delayTime * 1000);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }

            String message = "<div class=\"errorMsg\">You had more than " + delayThreshold + " unsuccessful registration attempts, execution was delayed by " + delayTime + " seconds.</div>";
            errors.add(message);

        } else if (retries != null) {

            session.setAttribute(NUMBER_OF_REGISTRATION_ATTEMPTS, retries + 1);

        } else {

            session.setAttribute(NUMBER_OF_REGISTRATION_ATTEMPTS, 1);

        }

    }
}
