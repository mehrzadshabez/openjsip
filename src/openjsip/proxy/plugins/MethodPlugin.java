/**
 * This file is part of OpenJSIP, a free SIP service components.
 *
 * OpenJSIP is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version
 *
 * OpenJSIP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Copyright (c) 2009 - Yevgen Krapiva
 */
package openjsip.proxy.plugins;

import openjsip.proxy.Proxy;

import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.Properties;

/**
 * Method plugins are plugins that processes messages that are to be handled by Proxy.
 * These messages include messages like OPTIONS, REGISTER, PUBLISH etc.
 *
 * Method plugin interface is a subject of design. 
 */

public interface MethodPlugin
{
    /**
     * Initializes this plugin
     * @param props Properties (settings) for this plugin
     * @param proxy Proxy (here will be interface in future)
     * @throws MethodPluginException if plugin was unable to initialize
     */
    public void initialize(Properties props, Proxy proxy) throws MethodPluginException;

    /**
     * @return whether this plugin was initialized
     */
    public boolean isInitialized();

    /**
     * @return the name of method that is processed by this plugin
     */
    public String getMethod();

    /**
     * Processes request
     * @param request Request to process
     * @return Response that is to be returned to the client, or null if plugin was unable to process it
     * @throws MethodPluginException If something goes wrong
     */
    public Response processRequest(Request request) throws MethodPluginException;
}
