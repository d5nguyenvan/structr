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
package org.structr.core.entity.log;

import org.structr.core.entity.NodeList;
import org.structr.core.entity.AbstractNode;

public class LogNodeList<T extends AbstractNode> extends NodeList<T> {

//    private final GraphDatabaseService graphDb = (GraphDatabaseService) Services.createCommand(GraphDatabaseCommand.class).execute();

    public LogNodeList() {
//        // add creating decorator..
//        addDecorator(new Decorator<T>() {
//
//            @Override
//            public void decorate(AbstractNode t) {
//                t.init(graphDb.createNode());
//            }
//        });
//
//        // add creating decorator..
//        addDecorator(new Decorator<T>() {
//
//            @Override
//            public void decorate(AbstractNode t) {
//                if (t instanceof Activity) {
//                    ((Activity) t).setStartTimestamp(new Date());
//                }
//            }
//        });
    }
}
