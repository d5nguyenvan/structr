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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.structr.common.CurrentRequest;
import org.structr.common.RenderMode;
import org.structr.core.Command;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.ArbitraryNode;
import org.structr.core.node.NodeFactoryCommand;

/**
 *
 * @author axel
 */
public class WebNode extends ArbitraryNode {

    private static final Logger logger = Logger.getLogger(AbstractNode.class.getName());

    protected final static String SESSION_BLOCKED = "sessionBlocked";
    protected final static String USERNAME_KEY = "username";

    /**
     * Traverse over all child nodes to find a home page
     */
    public HomePage getHomePage() {

        List<AbstractNode> childNodes = getAllChildren(HomePage.class.getSimpleName());

        for (AbstractNode node : childNodes) {

            if (node instanceof HomePage) {
                return ((HomePage) node);
            }

        }
        logger.log(Level.FINE, "No home page found for node {0}", this.getId());
        return null;
    }

    
    /**
     * Return the node path of next ancestor site (or domain, if no site exists),
     * or the root node, if no domain or site is in the
     * path.
     * 
     * @return 
     */
    public String getContextPath() {
        
        int sublevel = 0;
        
        List<AbstractNode> ancestors = getAncestorNodes();
        for (AbstractNode n : ancestors) {
            
            if (n instanceof Site || n instanceof Domain) {
                sublevel++;
            }
            
        }
        
        StringBuilder path = new StringBuilder();
        for (int i=1; i<sublevel; i++) {
            path.append("../");
        }
        return path.toString();
    }
    
    /**
     * Assemble URL for this node.
     *
     * This is an inverse method of @getNodeByIdOrPath.
     *
     * @param renderMode
     * @param contextPath
     * @return
     */
    public String getNodeURL(final Enum renderMode, final String contextPath) {

        String domain = "";
        String site = "";
        String path = "";


        if (RenderMode.PUBLIC.equals(renderMode)) {

            // create bean node
            Command nodeFactory = Services.command(NodeFactoryCommand.class);
            AbstractNode node = (AbstractNode) nodeFactory.execute(this);

            // stop at root node
            while (node != null && node.getId() > 0) {

                String urlPart = node.getName();
                
                if (urlPart != null) {
                    if (node instanceof Site) {
                        site = urlPart;
                    } else if (node instanceof Domain) {
                        domain = urlPart;
                    } else {
                        path = urlPart;
                    }
                }

                // check parent nodes
                node = node.getParentNode();
//                StructrRelationship r = node.getRelationships(RelType.HAS_CHILD, Direction.INCOMING).get(0);
//                if (r != null) {
//                    node = r.getStartNode();
//                }
            }

            String scheme = CurrentRequest.getRequest().getScheme();
            
            return scheme + "://" + site + (StringUtils.isNotEmpty(site) ? "." : "") + domain + "/" + path;

        } else if (RenderMode.LOCAL.equals(renderMode)) {
            // assemble relative URL following the pattern
            // /<context-url>/view.htm?nodeId=<path>
            // TODO: move this to a better place
            // TODO: improve handling for renderMode
            String localUrl = contextPath.concat(getNodePath()).concat("&renderMode=local");
            return localUrl;

        } else {
            // TODO implement other modes
            return null;
        }
    }
    
    public String getNodeURL(final String contextPath) {
        return getNodeURL(RenderMode.PUBLIC, contextPath);
    }

    @Override
    public String getIconSrc() {
        return "/images/folder.png";
    }

    @Override
    public void onNodeCreation()
    {
    }

    @Override
    public void onNodeInstantiation()
    {
    }
}
