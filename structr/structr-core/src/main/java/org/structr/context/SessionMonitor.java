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
package org.structr.context;

import java.util.LinkedList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.structr.common.CurrentRequest;
import org.structr.common.RelType;
import org.structr.core.Command;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.User;
import org.structr.core.entity.log.Activity;
import org.structr.core.entity.log.LogNodeList;
import org.structr.core.entity.log.PageRequest;
import org.structr.core.log.LogCommand;
import org.structr.core.node.CreateNodeCommand;
import org.structr.core.node.CreateRelationshipCommand;
import org.structr.core.node.NodeAttribute;

/**
 * Helper class for handling session management
 *
 * @author axel
 */
public class SessionMonitor {
    
    private static final Logger logger = Logger.getLogger(SessionMonitor.class.getName());
    
    private static final String USER_LIST_KEY = "userList";
    private static List<Session> sessions = null;
    public static final String SESSION_ID = "sessionId";

    /**
     * This class holds information about a user session, e.g.
     * the user object, the login and logout time, and a list
     * with activities.
     *
     */
    // <editor-fold defaultstate="collapsed" desc="Session">
    public static class Session {

        private long id;
        private String uid;
        private State state;
        private User user;
        private Date loginTimestamp;
        private Date logoutTimestamp;
//        private LogNodeList<Activity> activityList;
        private Map<String, Object> lastActivityProperties;

        public Session(final long id, String uid, final User user, Date loginTime) {
            this.id = id;
            this.uid = uid;
            this.state = State.ACTIVE;
            this.user = user;
            this.loginTimestamp = loginTime;
//            this.activityList = getActivityList();
        }

        private boolean hasActivity() {
            Activity lastActivity = getLastActivity();
            return (lastActivity != null);
        }

        /**
         * @return last activity type
         */
        public String getLastActivityType() {
            Activity lastActivity = getLastActivity();
            return lastActivity != null ? getLastActivity().getType() : null;
        }

        /**
         * @return last activity start timestamp
         */
        public Date getLastActivityStartTimestamp() {
            return hasActivity() ? getLastActivity().getStartTimestamp() : null;
        }

        /**
         * @return last activity end timestamp
         */
        public Date getLastActivityEndTimestamp() {
            return hasActivity() ? getLastActivity().getEndTimestamp() : null;
        }

        /**
         * @return last activity URI
         */
        public String getLastActivityText() {
            return hasActivity() ? getLastActivity().getActivityText() : null;
        }

        /**
         * Return time since last activity
         * @return time since last activity
         */
        public Long getInactiveSince() {
            if (hasActivity()) {
                return (new Date()).getTime() - getLastActivityEndTimestamp().getTime();
            }
            return null;
        }

        public String getUserName() {
            if (user == null) {
                return null;
            }
            return user.getName();
        }

        public User getUser() {
            return user;
        }

        public void setUser(final User user) {
            this.user = user;
        }

        public Date getLoginTimestamp() {
            return loginTimestamp;
        }

        public void setLoginTimestamp(final Date loginTimestamp) {
            this.loginTimestamp = loginTimestamp;
        }

        public LogNodeList<Activity> getActivityList() {

            LogNodeList<Activity> activityList;
            // First, try to find user's activity list
            for (AbstractNode s : user.getDirectChildNodes()) {
                if (s instanceof LogNodeList) {

                    // Take the first LogNodeList
                    activityList = (LogNodeList) s;
                    return activityList;
                }
            }

            // Create a new activity list as child node of the respective user
            Command createNode = Services.command(CreateNodeCommand.class);
            Command createRel = Services.command(CreateRelationshipCommand.class);

            activityList = (LogNodeList<Activity>) createNode.execute(user,
                    new NodeAttribute(AbstractNode.TYPE_KEY, LogNodeList.class.getSimpleName()),
                    new NodeAttribute(AbstractNode.NAME_KEY, user.getName() + "'s Activity Log"));
//            activityList = new LogNodeList<Activity>();
//            activityList.init(s);

            createRel.execute(user, activityList, RelType.HAS_CHILD);

            return activityList;
        }

        private void setLastActivity(final Activity activity) {
            lastActivityProperties = activity.getPropertyMap();
        }

        private Activity getLastActivity() {
            Activity a = new Activity(lastActivityProperties);
            return a;
        }

//        public void setActivityList(final List<Activity> activityList) {
//            this.setActivityList(activityList);
//        }
        public Date getLogoutTimestamp() {
            return logoutTimestamp;
        }

        public void setLogoutTime(final Date logoutTime) {
            this.logoutTimestamp = logoutTime;
        }

        public long getId() {
            return id;
        }

        public void setId(final long id) {
            this.id = id;
        }

        public String getUid() {
            return uid;
        }

        /**
         * @param uid the uid to set
         */
        public void setUid(final String uid) {
            this.uid = uid;
        }

//        public void addActivity(final Activity activity) {
//            activityList.add(activity);
//        }
        public State getState() {
            return state;
        }

        public void setState(final State state) {
            this.state = state;
        }
    }// </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="State">
    public enum State {

        UNDEFINED, ACTIVE, INACTIVE, WAITING, CLOSED, STARTED, FINISHED;
    }// </editor-fold>

    /**
     * Return list with all sessions
     * 
     * @return
     */
    public static List<Session> getSessions() {
        return sessions;
    }

    public static long getSessionByUId(final String sessionUid) {
        for (Session session : sessions) {
            if (session.getUid().equals(sessionUid)) return session.getId();
        }
        return -1L;
    }

    /**
     * Register user in servlet context
     */
    public static long registerUserSession(final HttpSession session) {
        User user = CurrentRequest.getCurrentUser();
        init(session.getServletContext());
        long id = nextId(session.getServletContext());
        sessions.add(new Session(id, session.getId(), user, new Date()));
        session.getServletContext().setAttribute(USER_LIST_KEY, sessions);
        return id;

    }

    /**
     * Unregister user in servlet context
     */
    public static void unregisterUserSession(final long id, final ServletContext context) {

        init(context);
        Session session = getSession(id);
        session.setLogoutTime(new Date());
        session.setState(State.INACTIVE);
//        context.setAttribute(USER_LIST_KEY, sessions);

    }

    /**
     * Append an activity to the activity list
     * 
     * @param sessionId
     * @param action
     */
    public static void logActivity(final long sessionId, final String action) {

        Date now = new Date();

        User user = CurrentRequest.getCurrentUser();

        // Create a "dirty" activity node
        Activity activity = new Activity();
        activity.setProperty(AbstractNode.TYPE_KEY, Activity.class.getSimpleName());

        if (user != null) {
            activity.setProperty(AbstractNode.NAME_KEY, "User: " + user.getName() + ", Action: " + action + ", Date: " + now);
        }

        activity.setProperty(Activity.SESSION_ID_KEY, sessionId);
        activity.setProperty(Activity.START_TIMESTAMP_KEY, now);
        activity.setProperty(Activity.END_TIMESTAMP_KEY, now);
        activity.setUser(user);

        getSession(sessionId).setLastActivity(activity);
        Services.command(LogCommand.class).execute(activity);
    }

    /**
     * Append a page request to the activity list
     *
     * @param sessionId
     * @param action
     */
    public static void logPageRequest(final long sessionId, final String action, final HttpServletRequest request) {

        long t0 = System.currentTimeMillis();
        
        Date now = new Date();

        User user = CurrentRequest.getCurrentUser();

        // Create a "dirty" page request node
        PageRequest pageRequest = new PageRequest();
        pageRequest.setProperty(AbstractNode.TYPE_KEY, PageRequest.class.getSimpleName());
        pageRequest.setProperty(AbstractNode.NAME_KEY, action + ", Date: " + now);
        
        StringBuilder text = new StringBuilder();
        text.append("{");
        
        if (request != null) {
            
            Map<String,Object> parameterMap = request.getParameterMap();
            Set<String> keys = parameterMap.keySet();
            for (String key : keys) {
                text.append("\"").append(key).append("\": \"").append(request.getParameter(key)).append("\", ");
            }
            
            
            text.append("\"uri\": \"").append(request.getRequestURI()).append("\", ");
            text.append("\"remoteAddress\": \"").append(request.getRemoteAddr()).append("\", ");
            text.append("\"remoteHost\": \"").append(request.getRemoteHost()).append("\"");
            
        }

        pageRequest.setProperty(Activity.SESSION_ID_KEY, sessionId);
        pageRequest.setProperty(Activity.START_TIMESTAMP_KEY, now);
        pageRequest.setProperty(Activity.END_TIMESTAMP_KEY, now);
        pageRequest.setUser(user);

        text.append("}");
        pageRequest.setActivityText(text.toString());
        
        getSession(sessionId).setLastActivity(pageRequest);
        Services.command(LogCommand.class).execute(pageRequest);
        
        long t1 = System.currentTimeMillis();
        
        logger.log(Level.FINE, "Logging of page request took {0} ms", (t1-t0)/1000);
    }
    // ---------------- private methods ---------------------    
    // <editor-fold defaultstate="collapsed" desc="private methods">

    private static Session getSession(final long sessionId) {
        for (Session s : sessions) {
            if (s.getId() == sessionId) {
                return s;
            }
        }
        return null;
    }

    private static void init(final ServletContext context) {
        if (sessions == null) {
            sessions = (List<Session>) context.getAttribute(USER_LIST_KEY);
        }

        if (sessions == null) {
            sessions = new LinkedList<Session>();
        }
    }

    private static long nextId(final ServletContext context) {
        init(context);
        return sessions.size();
    }
    // </editor-fold>
}