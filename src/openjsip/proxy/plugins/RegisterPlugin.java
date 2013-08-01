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

import openjsip.remote.registrar.RegistrarInterface;
import openjsip.proxy.Proxy;

import javax.sip.message.Response;
import javax.sip.message.Request;
import javax.sip.address.URI;
import javax.sip.address.SipURI;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * Method plugin to handle REGISTER requests.
 * This plugin connects to Registrar via RMI and forwards it REGISTER request.
 */
public class RegisterPlugin implements MethodPlugin
{
    /**
     * Logger
     */
    private static Logger log = Logger.getLogger(RegisterPlugin.class);

    /**
     * Proxy interface
     */
    private Proxy proxy;

    /**
     * Initialization flag
     */
    private boolean isInitialized;

    /**
     * Registrar service connection variables
     */
    private String registrarServiceName;
    private String registrarServiceHost;
    private int registrarServicePort = 1099;

    
    public void initialize(Properties props, Proxy proxy) throws MethodPluginException
    {
        this.proxy = proxy;

        registrarServiceName = props.getProperty("proxy.method.plugin." + getClass().getName() + ".registrar.rmi.objectname", "Registrar").trim();
        registrarServiceHost = props.getProperty("proxy.method.plugin." + getClass().getName() + ".registrar.rmi.host", "localhost").trim();
        registrarServicePort = 1099;

        try
        {
            registrarServicePort = Integer.parseInt(props.getProperty("proxy.method.plugin." + getClass().getName() + ".registrar.rmi.port", "1099").trim());
        }
        catch (NumberFormatException ex)
        {
            // ignored
        }

        isInitialized = true;
    }

    public boolean isInitialized()
    {
        return isInitialized;
    }

    public String getMethod()
    {
        return Request.REGISTER;
    }

    /**
     * @return remote registrar instance.
     */
    private RegistrarInterface getRegistrar()
    {
        try
        {
            Registry registry = LocateRegistry.getRegistry(registrarServiceHost, registrarServicePort);
            RegistrarInterface registrar = (RegistrarInterface) registry.lookup(registrarServiceName);
            return registrar;
        }
        catch (Exception ex)
        {
            log.error("Cannot connect to remote Registrar server: " + ex.getMessage());
            if (log.isTraceEnabled())
                log.trace("Cannot connect to remote Registrar server.", ex);

            return null;
        }
    }

    public Response processRequest(Request request)  throws MethodPluginException
    {
        Response response = null;

        RegistrarInterface registrar = getRegistrar();
        if (registrar == null)
            throw new MethodPluginException("Cannot connect to remote Registrar server.");
        
        try
        {
            if (request.getMethod().equals(Request.REGISTER))
            {
                // We'll say it again. Location service and registrar work in
                // terms of domains, not IP addresses.
                // So if domain part in request matches proxie's interface
                // we say registrar that it needs to change a domain to the new one.
                String overridenDomain = null;
                URI requestURI = request.getRequestURI();
                if (requestURI.isSipURI())
                {
                    String host = ((SipURI) requestURI).getHost();
                    if (proxy.addrMatchesInterface(host))
                        overridenDomain = proxy.getDefaultDomain();
                }


                    response = registrar.processRequest(request, overridenDomain);
            }
        }
        catch (Exception ex)
        {
            throw new MethodPluginException(ex.getMessage(), ex.getCause());
        }

        return response;
    }

}
