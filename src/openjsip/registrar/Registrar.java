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
package openjsip.registrar;

import openjsip.auth.DigestServerAuthenticationMethod;
import openjsip.remote.locationservice.UserNotFoundException;
import openjsip.remote.locationservice.LocationServiceInterface;
import openjsip.remote.locationservice.Binding;
import openjsip.SipUtils;
import openjsip.snmp.SNMPAssistant;
import openjsip.remote.registrar.RegistrarInterface;

import javax.sip.*;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.sip.address.AddressFactory;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import java.util.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;
import java.net.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.NDC;
import gov.nist.javax.sip.message.SIPResponse;
import snmp.SNMPv1AgentInterface;
import snmp.SNMPCounter32;
import snmp.SNMPInteger;


public class Registrar extends UnicastRemoteObject implements SipListener, RegistrarInterface, Runnable
{
    /**
     * Logger
     */
    private static Logger log = Logger.getLogger(Registrar.class);
 
    /**
     * Main SIP stack
     */
    private SipStack sipStack;

    /**
     * Factory that creates other factories :)
     */
    private SipFactory sipFactory;

    /**
     * Factory that constructs address fields
     */
    private AddressFactory addressFactory;

    /**
     * Factory that constructs headers
     */
    private HeaderFactory headerFactory;

    /**
     * Factory that constructs entire SIP messages
     */
    private MessageFactory messageFactory;

    /**
     * Set of responsible domains
     */
    private final HashSet<String> domains = new HashSet<String>();

    /**
     * Class that performs authentication 
     */
    private DigestServerAuthenticationMethod dsam;

    /**
     * The set where all IP addresses and hostnames registrar is listening for are stored.
     */
    private final HashSet<String> ipAndHostNames = new HashSet <String> ();

    /**
     * Location service connection variables
     */
    private String locationServiceName;
    private String locationServiceHost;
    private int locationServicePort = 1099;

    /**
     * The minimum allowed time for binding to expire
     */
	public final int BINDING_EXPIRE_TIME_MIN;

    /**
     * The maximum allowed time for binding to expire
     */
    public final int BINDING_EXPIRE_TIME_MAX;

    /**
     * Operation mode ( standalone or viaproxy )
     * Standalone means that Registrar listening network interfaces for incoming REGISTER requests.
     * ViaProxy means that Proxy listening for requests and forwards REGISTER requests to Registrar via RMI.
     * In the last case, SipStack, SipProviders, etc are not used.
     */
    private static boolean standaloneMode;

    /**
     * Flag that enables or disables subscriber's authentication
     */    
    private boolean authenticationEnabled;

    /**
     * RMI binding name
     */
    private static String RMIBindName;

    /**
     * SNMP package used here is experimental and is likely to be
     * substituted by SNMP4JAgent in future. Due to lack of good documentation
     * on SNMP4J it cannot be done for now.
     */

    /**
     * SNMP agent engine
     */
    private SNMPv1AgentInterface agentInterface;

    /**
     * Additional class to ease the work with SNMP.
     */
    private SNMPAssistant snmpAssistant;

    /**
     * SNMP root oid. This is where all our objects reside.
     * The current value corresponds to .iso.org.dod.internet.private.enterprises.
     * 1937 is our random generated value for (OpenJSIP), but normally this number is to be
     * given by IANA.
     * The next value correspond to:
     * 1 - OpenJSIP Location service
     * 2 - OpenJSIP Registrar service
     * 3 - OpenJSIP Proxy service
     */
    protected static final String SNMP_ROOT_OID = "1.3.6.1.4.1.1937.2.";
    protected static final String SNMP_OID_NUM_INCOMING_REQUESTS = SNMP_ROOT_OID + "1.1";
    protected static final String SNMP_OID_NUM_RESPONSES_OK = SNMP_ROOT_OID + "1.2";
    protected static final String SNMP_OID_NUM_REQUEST_REJECTS = SNMP_ROOT_OID + "1.3";
    protected static final String SNMP_OID_NUM_REQUEST_PROCESSING_ERRORS = SNMP_ROOT_OID + "1.4";
    protected static final String SNMP_OID_NUM_BINDING_QUERIES = SNMP_ROOT_OID + "1.5";
    protected static final String SNMP_OID_NUM_AUTH_FAILED = SNMP_ROOT_OID + "1.6";
    protected static final String SNMP_OID_NUM_USER_NOT_FOUND = SNMP_ROOT_OID + "1.7";
    protected static final String SNMP_OID_NUM_BINDING_UPDATES = SNMP_ROOT_OID + "1.8";
    protected static final String SNMP_OID_NUM_BINDING_CANCELS = SNMP_ROOT_OID + "1.9";
    protected static final String SNMP_OID_NUM_BINDING_FULL_CANCELS = SNMP_ROOT_OID + "1.10";

    /**
     * SNMP database with default values.
     */
    private static final Object SNMP_DATABASE[][] = new Object[][]
    {
        { SNMP_OID_NUM_INCOMING_REQUESTS, new SNMPCounter32(0) },
        { SNMP_OID_NUM_RESPONSES_OK, new SNMPCounter32(0) },
        { SNMP_OID_NUM_REQUEST_REJECTS, new SNMPCounter32(0) },
        { SNMP_OID_NUM_REQUEST_PROCESSING_ERRORS, new SNMPCounter32(0) },
        { SNMP_OID_NUM_BINDING_QUERIES, new SNMPCounter32(0) },
        { SNMP_OID_NUM_AUTH_FAILED, new SNMPCounter32(0) },
        { SNMP_OID_NUM_USER_NOT_FOUND, new SNMPCounter32(0) },
        { SNMP_OID_NUM_BINDING_UPDATES, new SNMPCounter32(0) },
        { SNMP_OID_NUM_BINDING_CANCELS, new SNMPCounter32(0) },
        { SNMP_OID_NUM_BINDING_FULL_CANCELS, new SNMPCounter32(0) },
    };

    /**
     * Entry point
     * @param args arguments
     */
    public static void main(String[] args)
    {
        Properties props = null;

        
        if (args.length < 1)
        {
            printUsage();
            System.exit(1);
        }

        // Try to load properties file
        try
        {
            if (!new File(args[0]).exists())
            {
                System.err.println("Error: Cannot open configuration file " + args[0]);
                System.exit(1);
            }

            // Reading configuration data
            props = new Properties();
            props.load(new FileInputStream(args[0]));

            String externalLoggingConf = props.getProperty("logging.properties");
            if (externalLoggingConf != null)
                PropertyConfigurator.configure(externalLoggingConf.trim());
            else
                PropertyConfigurator.configure(props);
        }
        catch(IOException e)
        {
            System.err.println("Error: Cannot open configuration file " + args[0]);
            System.exit(1);
        }

        standaloneMode = props.getProperty("registrar.operation.mode", "viaproxy").trim().equalsIgnoreCase("standalone");

        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new SecurityManager());
        }

        RegistrarInterface registrar = null;

        try
        {
            registrar = new Registrar(props);
        }
        catch(Exception ex)
        {
            log.error(ex.getMessage());
            if (log.isTraceEnabled())
                log.trace("", ex);
            
            System.exit(1);
        }


        String name = props.getProperty("registrar.service.rmi.objectname", "Registrar").trim();
        String host = props.getProperty("registrar.service.rmi.host", "localhost").trim();
        int port = 1099;

        try
        {
            port = Integer.parseInt(props.getProperty("registrar.rmi.port", "1099").trim());
        }
        catch(NumberFormatException ex)
        {
            // ignored
        }

        RMIBindName = "rmi://" + host + ":" + port + "/" + name;

        try
        {
            Naming.rebind(RMIBindName, registrar);
        }
        catch(RemoteException ex)
        {
            log.error("Cannot register within RMI registry at " + host + ":" + port, ex);
            System.exit(1);
        }
        catch(MalformedURLException ex)
        {
            log.error("Cannot register within RMI registry at " + host + ":" + port, ex);
            System.exit(1);
        }
            
        if (log.isInfoEnabled())
            log.info("Registrar registered as \"" + name + "\" within RMI registry at " + host + ":" + port);

        if (log.isInfoEnabled())
            log.info("Registrar started...");
    }

    /**
     * Prints program usage help
     */
    private static void printUsage()
    {
        System.out.println("\nUsage: Registrar <registrar.properties file>\n" +
                           "   where registrar.properties is the path to .properties file with settings for Registrar server.");
    }

    /**
     * Registrar constructor
     * @param props Configuration Properties
     * @throws PeerUnavailableException
     * @throws ObjectInUseException
     * @throws TooManyListenersException
     * @throws TransportNotSupportedException
     * @throws InvalidArgumentException
     * @throws RemoteException
     */
    public Registrar(Properties props) throws PeerUnavailableException, ObjectInUseException, TooManyListenersException,
                                              TransportNotSupportedException, InvalidArgumentException, RemoteException
    {
        if (log.isInfoEnabled())
            log.info("Starting Registrar v" + SipUtils.OPENJSIP_VERSION + "...");

        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new SecurityManager());
        }

        String communityName = props.getProperty("registrar.snmp.agent.community", "public").trim();

        // Create our assistant class. This class should not be null even if SNMP is not enabled.
        snmpAssistant = new SNMPAssistant(communityName, SNMP_DATABASE);
      
        if (standaloneMode)
        {
            sipFactory = SipFactory.getInstance();

            sipFactory.setPathName("gov.nist");

            //No dialogs in registrations
            props.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");

            sipStack = sipFactory.createSipStack(props);
            
            if (log.isInfoEnabled())
                log.info("Configuring interfaces...");

            try
            {
                // If there are no configured hosts
                if (props.getProperty("registrar.interface.1.addr") == null)
                {
                    int index = 1;

                    Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                    while(nets.hasMoreElements())
                    {
                        NetworkInterface netInt = nets.nextElement();
                        Enumeration<InetAddress> inetAdresses = netInt.getInetAddresses();
                        while (inetAdresses.hasMoreElements())
                        {
                            InetAddress inetAddress = inetAdresses.nextElement();
                            props.setProperty("registrar.interface." + index + ".addr", inetAddress.getHostAddress());
                            index++;
                        }
                    }

                }
            }
            catch (SocketException e)
            {

            }

            int index = 1;
            String intface = null;
            boolean atLeastOneInterfaceConfigured = false;

            while ((intface = props.getProperty("registrar.interface." + index + ".addr")) != null)
            {
                intface = intface.trim();

                try
                {
                    if (log.isTraceEnabled())
                        log.trace("Configuring interface #" + index + ": " + intface);

                    // Get IP address of interface
                    InetAddress inetAddress = InetAddress.getByName(intface);

                    // Get desired locationServicePort
                    int proxyPort = 5060;

                    try
                    {
                        proxyPort = Integer.parseInt(props.getProperty("registrar.interface." + index + ".port", "5060").trim());
                    }
                    catch (NumberFormatException e)
                    {
                        // ignored
                    }
                    // Get list of transports
                    String[] transports = props.getProperty("registrar.interface." + index + ".transport", "udp, tcp").split(",");

                    // SipProvider represents an interface
                    SipProvider sipProvider = null;

                    // Transport is represented by ListeningPoint
                    for (String transport : transports)
                    {
                        try
                        {
                            if (log.isTraceEnabled())
                                log.trace("Creating ListeningPoint for " + inetAddress.getHostAddress() + ":" + proxyPort + " " + transport.trim());

                            // Try to create ListeningPoint for each transport
                            ListeningPoint lp = sipStack.createListeningPoint(inetAddress.getHostAddress(), proxyPort, transport.trim());

                            if (sipProvider == null)
                            {
                                if (log.isTraceEnabled())
                                    log.trace("Creating new SipProvider.");

                                sipProvider = sipStack.createSipProvider(lp);
                            }
                            else
                            {
                                if (log.isTraceEnabled())
                                    log.trace("Adding ListeningPoint to SipProvider.");

                                sipProvider.addListeningPoint(lp);
                            }
                        }
                        catch (Exception ex)
                        {
                            log.warn("Failed to create listening point " + inetAddress.getHostAddress() + ":" + proxyPort + " " + transport.trim() + " ( " + ex.getMessage() + " )");
                            if (log.isTraceEnabled())
                                log.trace("", ex);
                        }
                    }

                    // If interface has any ListeningPoints
                    if (sipProvider != null && sipProvider.getListeningPoints().length > 0)
                    {
                        ipAndHostNames.add(inetAddress.getHostAddress());
                        ipAndHostNames.add(inetAddress.getCanonicalHostName());

                        sipProvider.addSipListener(this);
                        atLeastOneInterfaceConfigured = true;
                    }
                }
                catch (java.net.UnknownHostException ex)
                {
                    log.warn("Interface #" + index + ": " + ex.getMessage());
                }
                finally
                {
                    index++;
                }
            }

            if (!atLeastOneInterfaceConfigured)
            {
                log.error("Cannot configure any interface. Registrar cannot be started.");
                System.exit(1);
            }

            // Print configuration info
            Iterator sipProviders = sipStack.getSipProviders();
            index = 1;
            
            while (sipProviders.hasNext())
            {
                SipProvider sipProvider =  (SipProvider) sipProviders.next();
                ListeningPoint[] lps = sipProvider.getListeningPoints();

                StringBuffer sb = new StringBuffer();
                sb.append("Interface #" + index + ": ");

                for (int i=0; i<lps.length; i++)
                {
                    if (i == 0)
                        sb.append(lps[i].getIPAddress() + ":" + lps[i].getPort() + " via ");

                    sb.append(lps[i].getTransport());

                    if (i < lps.length -1)
                        sb.append(", ");
                }


                if (log.isInfoEnabled())
                    log.info(sb.toString());

                index++;
            }

        }

        locationServiceName = props.getProperty("registrar.location.service.rmi.objectname", "Location Service").trim();
        locationServiceHost = props.getProperty("registrar.location.service.rmi.host", "localhost").trim();

        try
        {
            locationServicePort = Integer.parseInt(props.getProperty("registrar.location.service.rmi.port", "1099").trim());
        }
        catch(NumberFormatException ex)
        {
            // ignored
        }

        if (log.isInfoEnabled())
            log.info("Connecting to Location Service server at "+ locationServiceHost +":"+ locationServicePort +" ...");

        LocationServiceInterface locationService = getLocationService();
        if (locationService != null && locationService.isAlive())
        {
            if (log.isInfoEnabled())
                log.info("Successfully connected.");
        }
        else
            throw new RemoteException("Cannot connect to Location Service server.");


        if (sipStack == null)
        {
            sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");

            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", "Registrar");

            sipStack = sipFactory.createSipStack(properties);
        }
        
        headerFactory = sipFactory.createHeaderFactory();

        addressFactory = sipFactory.createAddressFactory();

        messageFactory = sipFactory.createMessageFactory();             

        String domainsStr = props.getProperty("registrar.domains");
        if (domainsStr != null)
        {
            String[] domainsArray = domainsStr.trim().split(",");

            for (String aDomainsArray : domainsArray)
            {
                String s = aDomainsArray.trim();
                if (s.length() > 0) domains.add(s);
            }
        }

        if (domains.isEmpty())
        {
            log.warn("No domains configured. Retreiving the domain list from Location Service...");

            domains.addAll(locationService.getDomains());
        }

        if (domains.isEmpty())
        {
            log.error("No domains configured. Registrar cannot be started.");
            System.exit(1);
        }

        if (log.isInfoEnabled())
        {
            StringBuffer sb = new StringBuffer();
            Iterator it = domains.iterator();

            while (it.hasNext())
            {
                sb.append((String) it.next());
                if (it.hasNext()) sb.append(", ");
            }

            log.info("Registrar is responsible for domains: " + sb.toString());
        }

        authenticationEnabled = props.getProperty("registrar.authentication.enabled", "no").trim().equalsIgnoreCase("yes");
        if (log.isInfoEnabled())
        {
            if (authenticationEnabled)
                log.info("Authentication enabled.");
            else
                log.info("Authentication disabled.");
        }
        
        try
        {
            dsam = new DigestServerAuthenticationMethod(getDefaultDomain(), new String[] { "MD5" });
        }
        catch (NoSuchAlgorithmException ex)
        {
            log.error("Cannot create authentication method. Some algorithm is not implemented: " + ex.getMessage());
            if (log.isTraceEnabled())
                log.trace(null, ex);

            System.exit(1);
        }

        int timeMin = 60;
        int timeMax = 3600;

        try
        {
            timeMin = Integer.parseInt(props.getProperty("registrar.binding.expire.time.min", "60").trim());
        }
        catch(NumberFormatException ex)
        {
            // ignored
        }

        try
        {
            timeMax = Integer.parseInt(props.getProperty("registrar.binding.expire.time.max", "3600").trim());
        }
        catch(NumberFormatException ex)
        {
            // ignored
        }

        BINDING_EXPIRE_TIME_MIN = Math.max(5, timeMin);
        BINDING_EXPIRE_TIME_MAX = Math.max(BINDING_EXPIRE_TIME_MIN + 5, timeMax);

         if (log.isInfoEnabled())
             log.info("Registration lifetime (seconds): min "+ BINDING_EXPIRE_TIME_MIN +", max "+ BINDING_EXPIRE_TIME_MAX);

        /**
         * Read SNMP configuration
         */
        boolean isSnmpEnabled = props.getProperty("registrar.snmp.agent.enabled", "yes").trim().equalsIgnoreCase("yes");
        if (isSnmpEnabled)
        {
            int snmpPort = 1162;

            try
            {
                snmpPort = Integer.parseInt(props.getProperty("registrar.snmp.agent.port", "1162").trim());
            }
            catch (NumberFormatException e)
            {
                /* ignored */
            }

            try
            {
                // Create SNMP agent engine
                agentInterface = new SNMPv1AgentInterface(0 /* SNMP v1 */, snmpPort, null);

                // Run agent
                agentInterface.addRequestListener(snmpAssistant);
                agentInterface.setReceiveBufferSize(5120);
                agentInterface.startReceiving();

                if (log.isInfoEnabled())
                    log.info("SNMP agent started at port "+snmpPort+" with community "+communityName);
            }
            catch(SocketException ex)
            {
                log.error("Cannot start SNMP agent at port "+snmpPort+": "+ex.getMessage());
            }
        }

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this));
    }

    /**
     * Shutdown hook
     */
    public void run()
    {
        if (log != null && log.isInfoEnabled())
            log.info("Shutting down...");

        // Stop SNMP agent
        try
        {
            if (agentInterface != null)
                agentInterface.stopReceiving();
        }
        catch (SocketException ex)
        {
            /* ignored */
        }

        try
        {
            if (!standaloneMode)
                Naming.unbind(RMIBindName);
        }
        catch (Exception e)
        {
            /* ignored */
        }
    }

    private LocationServiceInterface getLocationService()
    {
        try
        {
            Registry registry = LocateRegistry.getRegistry(locationServiceHost, locationServicePort);
            return (LocationServiceInterface) registry.lookup(locationServiceName);
        }
        catch (RemoteException ex)
        {
            return null;
        }
        catch (NotBoundException ex)
        {
            return null;
        }
    }

    /**
     * @see javax.sip.SipListener
     */
    public void processRequest(RequestEvent requestEvent)
    {
        snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_INCOMING_REQUESTS);

        Request request = requestEvent.getRequest();
        ServerTransaction serverTransaction = requestEvent.getServerTransaction();
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();

        try
        {
            if (request.getMethod().equals(Request.REGISTER))
            {
                String overridenDomain = null;
                URI requestURI = request.getRequestURI();
                if (requestURI.isSipURI())
                {
                    String host = ((SipURI) requestURI).getHost();
                    if (addrMatchesInterface(host))
                        overridenDomain = getDefaultDomain();
                }

                Response response = processRequest(request, overridenDomain);

                if (serverTransaction != null)
                    serverTransaction.sendResponse(response);
                else
                    sipProvider.sendResponse(response);

                if (response.getStatusCode() == Response.OK)
                    snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_RESPONSES_OK);
                else
                    snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_REQUEST_REJECTS);
            }
        }       
        catch (Exception ex)
        {
            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_REQUEST_PROCESSING_ERRORS);
            
            SipUtils.sendResponseSafe(Response.SERVER_INTERNAL_ERROR, (SipProvider) requestEvent.getSource(), messageFactory, request, serverTransaction);
            log.error("", ex);
        }
    }

    /**
     * @see openjsip.remote.registrar.RegistrarInterface
     */
    public Response processRequest(Request request, String domain) throws RemoteException
    {
        snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_INCOMING_REQUESTS);

        CallIdHeader callidHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);

        // Place Call-ID header to each log message
        NDC.push(callidHeader != null ? callidHeader.getCallId() : Long.toString(System.currentTimeMillis()));


        if (log.isDebugEnabled())
        {
            log.debug("-------------------");
            log.debug("Incoming REGISTER request " + request.getRequestURI() + " " + request.getHeader(ToHeader.NAME).toString().trim());
        }

        if (log.isTraceEnabled())
            log.trace("REQUEST: \n" + request + "\n");

        Response response = null;

        try
        {
            response = processRegister(request, domain);
        }
        catch (Exception ex)
        {
            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_REQUEST_PROCESSING_ERRORS);

            if (log.isDebugEnabled())
                log.debug("Exception raised: " + ex.getMessage());
        }

        if (response != null)
        {
            if (response.getStatusCode() == Response.OK)
                snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_RESPONSES_OK);
            else
                snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_REQUEST_REJECTS);
            
            if (log.isTraceEnabled())
                log.trace("RESPONSE: \n" + response + "\n");

            if (log.isDebugEnabled())
                log.debug("Replied " + response.getStatusCode() + " ( " + SIPResponse.getReasonPhrase(response.getStatusCode()) + " ) ");
        }

        NDC.remove();

        return response;
    }

    /**
     * @see javax.sip.SipListener
     */
    public void processResponse(ResponseEvent event)
    {
        // No responses in registration
    }

    /**
     * @see javax.sip.SipListener
     */
    public void processTimeout(TimeoutEvent event)
    {
        if (log.isDebugEnabled())
            log.debug("Timeout event occured. Event: "+event.toString());
    }

    /**
     * @see javax.sip.SipListener
     */
    public void processIOException(IOExceptionEvent event)
    {
        if (log.isDebugEnabled())
            log.debug("IOException occured in SipProvider at "+event.getHost()+":"+event.getPort()+" /"+event.getTransport()+" Source: "+event.getSource());

    }

    /**
     * @see javax.sip.SipListener
     */
    public void processTransactionTerminated(TransactionTerminatedEvent event)
    {
        if (log.isDebugEnabled())
            log.debug("Transaction terminated. Event: "+event.toString());
    }

    /**
     * @see javax.sip.SipListener
     */
    public void processDialogTerminated(DialogTerminatedEvent event)
    {
        // No dialogs in registrations
    }

    /**
     * This function actually performs registration process.
     * @param request Original REGISTER request
     * @param correctDomain If not null, host part of To header value will be replaced with this <i>correctDomain</i>
     * @return Response to be sent back to client.
     * @throws ParseException JAIN-SIP internal errors.
     * @throws RemoteException Error communicating with location service.
     * @throws InvalidArgumentException JAIN-SIP internal errors.
     */
    private Response processRegister(Request request, String correctDomain) throws ParseException, RemoteException, InvalidArgumentException
    {
        Request fixedRequest = null;

        /**
         * If host part in request contains interface address, we should replace it with correct domain
         * Then fixedRequest instance will be used when accessing the location service,
         * and original request will be used when replying
         */
        if (correctDomain != null)
        {
            fixedRequest = (Request) request.clone();

            ((SipURI) fixedRequest.getRequestURI()).setHost(correctDomain);

            URI toURI = ((ToHeader) fixedRequest.getHeader(ToHeader.NAME)).getAddress().getURI();
            if (toURI.isSipURI())
                ((SipURI) toURI).setHost(correctDomain);
        }

        /**
         * First, taking in mind the following:
         *
         * The following header fields, except Contact, MUST be included in a
         * REGISTER request.  A Contact header field MAY be included:
         *
         * Request-URI: The Request-URI names the domain of the location
         * service for which the registration is meant (for example,
         * "sip:chicago.com").  The "userinfo" and "@" components of the
         * SIP URI MUST NOT be present.
         *
         * To: The To header field contains the address of record whose
         * registration is to be created, queried, or modified.  The To
         * header field and the Request-URI field typically differ, as
         * the former contains a user name.  This address-of-record MUST
         * be a SIP URI or SIPS URI.
         *
         * ...we consider that RequestURI and To header must contain SIP URI only
         */

        /**
         * 1. The registrar inspects the Request-URI to determine whether it
         * has access to bindings for the domain identified in the
         * Request-URI.  If not, and if the server also acts as a proxy
         * server, the server SHOULD forward the request to the addressed
         * domain, following the general behavior for proxying messages
         * described in Section 16.
         */
        URI tempURI = SipUtils.getCanonicalizedURI(fixedRequest == null ? request.getRequestURI() : fixedRequest.getRequestURI());

        if (tempURI == null || !tempURI.isSipURI())
        {
            if (log.isDebugEnabled())
                log.debug("Request rejected ( Request-URI is not SIP URI )");

            return messageFactory.createResponse(Response.BAD_REQUEST, request);
        }

        SipURI requestURI = (SipURI) tempURI;

        if (!isDomainServed(requestURI.getHost()))
        {
            Response response = messageFactory.createResponse(Response.FORBIDDEN, request);
            response.setReasonPhrase("The registrar is not responsible for domain " + requestURI.getHost() + ".");

            if (log.isDebugEnabled())
                log.debug("Request rejected ( " + response.getReasonPhrase() + " )");

            return response;
        }
        /**
         * 2. To guarantee that the registrar supports any necessary
         * extensions, the registrar MUST process the Require header field
         * values as described for UASs in Section 8.2.2.
         */

        ProxyRequireHeader prh = (ProxyRequireHeader) request.getHeader(ProxyRequireHeader.NAME);
        if (prh != null)
        {
            if (log.isDebugEnabled())
                log.debug("Request rejected ( No extensions supported )");

            // This version doesn't support any extensions

            // Let's return a 420 Bad Extension
            Response response = messageFactory.createResponse(Response.BAD_EXTENSION, request);

            // We have to add a Unsupported header listing the Option tags that we don't support:
            UnsupportedHeader unsupportedHeader = headerFactory.createUnsupportedHeader(prh.getOptionTag());
            response.setHeader(unsupportedHeader);

            return response;
        }

        // Get location service interface
        LocationServiceInterface locationService = getLocationService();
        if (locationService == null)
        {
            log.error("Cannot connect to Location Service server. Check if server is running and registered within RMI registry at target host.");
            return messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, request);
        }

        /**
         * 3. A registrar SHOULD authenticate the UAC.  Mechanisms for the
         * authentication of SIP user agents are described in Section 22.
         * Registration behavior in no way overrides the generic
         * authentication framework for SIP.  If no authentication
         * mechanism is available, the registrar MAY take the From address
         * as the asserted identity of the originator of the request.
         */

        try
        {
            if ( authenticationEnabled && !checkAuthorization(fixedRequest == null ? request : fixedRequest, dsam, locationService) )
            {
                if (log.isDebugEnabled())
                    log.debug("Request rejected ( Unauthorized )");

                Response response = messageFactory.createResponse(Response.UNAUTHORIZED,request);

                WWWAuthenticateHeader wwwAuthenticateHeader = headerFactory.createWWWAuthenticateHeader("Digest");
                wwwAuthenticateHeader.setParameter("realm",dsam.getDefaultRealm());
                wwwAuthenticateHeader.setParameter("nonce",dsam.generateNonce(dsam.getPreferredAlgorithm()));
                wwwAuthenticateHeader.setParameter("opaque","");
                wwwAuthenticateHeader.setParameter("stale","FALSE");
                wwwAuthenticateHeader.setParameter("algorithm", dsam.getPreferredAlgorithm());

                response.setHeader(wwwAuthenticateHeader);

                snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_AUTH_FAILED);
                return response;
            }
        }
        catch (UserNotFoundException e)
        {
            if (log.isDebugEnabled())
                log.debug(e.getMessage());

            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_USER_NOT_FOUND);
            return messageFactory.createResponse(Response.NOT_FOUND, request);
        }

        /**
         * 4. The registrar SHOULD determine if the authenticated user is
         * authorized to modify registrations for this address-of-record.
         * For example, a registrar might consult an authorization
         * database that maps user names to a list of addresses-of-record
         * for which that user has authorization to modify bindings.  If
         * the authenticated user is not authorized to modify bindings,
         * the registrar MUST return a 403 (Forbidden) and skip the
         * remaining steps.
         */

         // Authorized

        /**
         * 5. The registrar extracts the address-of-record from the To header
         * field of the request.  If the address-of-record is not valid
         * for the domain in the Request-URI, the registrar MUST send a
         * 404 (Not Found) response and skip the remaining steps.  The URI
         * MUST then be converted to a canonical form.  To do that, all
         * URI parameters MUST be removed (including the user-param), and
         * any escaped characters MUST be converted to their unescaped
         * form.  The result serves as an index into the list of bindings.
         */
        ToHeader toHeader = (ToHeader) (fixedRequest == null ? request : fixedRequest).getHeader(ToHeader.NAME);
        Address address = toHeader.getAddress();

        /**
         * The To header field and the Request-URI field typically differ, as
         * the former contains a user name.  This address-of-record MUST
         * be a SIP URI or SIPS URI.
         */
        tempURI = SipUtils.getCanonicalizedURI(address.getURI());

        if (!tempURI.isSipURI())
        {
            if (log.isDebugEnabled())
                log.debug("Request rejected ( To header is not SIP URI )");

            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_USER_NOT_FOUND);
            return messageFactory.createResponse(Response.NOT_FOUND, request);
        }

        if (!((SipURI) tempURI).getHost().equalsIgnoreCase(requestURI.getHost()))
        {
            if (log.isDebugEnabled())
                log.debug("Request rejected ( Domain parts in To and RequestURI are not equal. )");

            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_USER_NOT_FOUND);
            return messageFactory.createResponse(Response.NOT_FOUND, request);
        }

        // Key is the index to location service database.
        String key = SipUtils.getKeyToLocationService(fixedRequest == null ? request : fixedRequest);

        /**
         * 6. The registrar checks whether the request contains the Contact
         * header field.  If not, it skips to the last step.  If the
         * Contact header field is present, the registrar checks if there
         * is one Contact field value that contains the special value "*"
         * and an Expires field.  If the request has additional Contact
         * fields or an expiration time other than zero, the request is
         * invalid, and the server MUST return a 400 (Invalid Request) and
         * skip the remaining steps.  If not, the registrar checks whether
         * the Call-ID agrees with the value stored for each binding.  If
         * not, it MUST remove the binding.  If it does agree, it MUST
         * remove the binding only if the CSeq in the request is higher
         * than the value stored for that binding.  Otherwise, the update
         * MUST be aborted and the request fails.
         */

        // For usability we need contact headers stored in Vector rather than in iterator
        Vector<ContactHeader> contactHeaders = new Vector<ContactHeader>();

        ListIterator list = request.getHeaders(ContactHeader.NAME);
        while (list.hasNext())
        {
            ContactHeader contactHeader = (ContactHeader) list.next();
            contactHeaders.addElement(contactHeader);
        }

        if (!contactHeaders.isEmpty())
        {
            // We have one ore more contact headers

            ExpiresHeader expiresHeader = (ExpiresHeader) request.getHeader(ExpiresHeader.NAME);

            int wildcardIndex = 0;
            for (; wildcardIndex < contactHeaders.size(); wildcardIndex++)
            {
                ContactHeader contactHeader = contactHeaders.elementAt(wildcardIndex);
                if (contactHeader.isWildCard())
                    break;
            }

            // If wildcard contact header was found
            if (wildcardIndex < contactHeaders.size())
            {
                if (contactHeaders.size() != 1 || expiresHeader == null || expiresHeader.getExpires() != 0)
                {
                    if (log.isDebugEnabled())
                        log.debug("Request rejected ( Expires value is not valid )");

                    return messageFactory.createResponse(Response.BAD_REQUEST, request);
                }

                //Everything is ok, we can remove all bindings
                try
                {
                    locationService.removeAllBindings(key);
                    snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_BINDING_FULL_CANCELS);
                }
                catch (UserNotFoundException e)
                {
                    snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_USER_NOT_FOUND);
                    return messageFactory.createResponse(Response.NOT_FOUND, request);
                }

            }
            // Wildcard contact headers were are not found.
            // We process each contact header and add/update the existing bindings.
            else
            {
                /**
                 *  7. The registrar now processes each contact address in the Contact
                 * header field in turn.  For each address, it determines the
                 * expiration interval as follows:
                 *
                 * -  If the field value has an "expires" parameter, that value
                 *    MUST be taken as the requested expiration.
                 *
                 * -  If there is no such parameter, but the request has an
                 *    Expires header field, that value MUST be taken as the
                 *    requested expiration.
                 *
                 * -  If there is neither, a locally-configured default value MUST
                 *    be taken as the requested expiration.
                 */

                int expiresTime = BINDING_EXPIRE_TIME_MAX;

                // Get expiration time form Expires header if exists
                if (expiresHeader != null)
                {
                    expiresTime = expiresHeader.getExpires();
                }

                if (expiresTime != 0)
                {
                    // Correct time
                    expiresTime = Math.max(expiresTime, BINDING_EXPIRE_TIME_MIN);
                    expiresTime = Math.min(expiresTime, BINDING_EXPIRE_TIME_MAX);
                }
                
                // Move through each contact header
                for (int i=0; i<contactHeaders.size(); i++)
                {
                    ContactHeader contactHeader = contactHeaders.elementAt(i);

                    int contactExpiresTime = contactHeader.getExpires();

                    if (contactExpiresTime == -1)
                        contactExpiresTime = expiresTime;

                    if (contactExpiresTime != 0)
                    {
                        // Correct time
                        contactExpiresTime = Math.max(contactExpiresTime, BINDING_EXPIRE_TIME_MIN);
                        contactExpiresTime = Math.min(contactExpiresTime, BINDING_EXPIRE_TIME_MAX);
                    }

                    // Store expire time as contact parameter
                    contactHeader.setExpires(contactExpiresTime);

                    // Get existing bindings from location service
                    Binding existingBinding;

                    try
                    {
                        existingBinding = locationService.getBinding(key, contactHeader);
                    }
                    catch (UserNotFoundException e)
                    {
                        snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_USER_NOT_FOUND);
                        return messageFactory.createResponse(Response.NOT_FOUND, request);
                    }

                    String callId = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
                    long cseq = ((CSeqHeader) request.getHeader(CSeqHeader.NAME)).getSeqNumber();

                    // If existing binding was found
                    if (existingBinding != null)
                    {
                        /** If the binding does exist, the registrar checks the Call-ID value.
                         * If the Call-ID value in the existing binding differs from the
                         * Call-ID value in the request, the binding MUST be removed if
                         * the expiration time is zero and updated otherwise.  If they are
                         * the same, the registrar compares the CSeq value.  If the value
                         * is higher than that of the existing binding, it MUST update or
                         * remove the binding as above.  If not, the update MUST be
                         * aborted and the request fails.
                         */

                        if (callId.equals(existingBinding.getCallId()) && cseq <= existingBinding.getCseq())
                        {
                            return messageFactory.createResponse(Response.BAD_REQUEST, request);
                        }
                    }

                    //Everything is ok, we can update binding (or remove)

                    try
                    {
                        if (contactHeader.getExpires() == 0)
                        {
                            locationService.removeBinding(key, contactHeader);
                            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_BINDING_CANCELS);
                        }
                        else
                        {
                            locationService.updateRegistration(key, contactHeader, contactHeader.getExpires(), callId, cseq);
                            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_BINDING_UPDATES);
                        }
                    }
                    catch (UserNotFoundException e)
                    {
                        snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_USER_NOT_FOUND);
                        return messageFactory.createResponse(Response.NOT_FOUND, request);
                    }

                }
            }
        }
        else
        {
            // Subscriber requests list of bindings
            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_BINDING_QUERIES);
        }

        // Return the existing bindings
        try
        {
            contactHeaders = locationService.getContactHeaders(key);
        }
        catch (UserNotFoundException e)
        {
            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_USER_NOT_FOUND);
            return messageFactory.createResponse(Response.NOT_FOUND, request);
        }

        Response response = messageFactory.createResponse(Response.OK, request);

        // Add contacts from bindings to response
        for (ContactHeader contactHeader : contactHeaders)
            response.addHeader(contactHeader);

        /**
         * The response SHOULD include a Date header field.
         */
        Calendar c = Calendar.getInstance();
        DateHeader dateHeader = headerFactory.createDateHeader(c);
        response.addHeader(dateHeader);
    
        return response;
    }

     /**
     * @return Default domain
     */
    public String getDefaultDomain()
    {
        return domains.iterator().next();
    }

    /**
     * Permors subscriber's authentication
     * @param request Request
     * @param dsam DigestServerAuthenticationMethod class
     * @param locationService Location service instance
     * @return true if subscriber was successfully authorized, false otherwise
     * @throws UserNotFoundException If subscriber was not found in location service database
     * @throws RemoteException ...
     */
    public boolean checkAuthorization(Request request, DigestServerAuthenticationMethod dsam, LocationServiceInterface locationService) throws UserNotFoundException, RemoteException
    {
        AuthorizationHeader authorizationHeader = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);

        if (authorizationHeader == null)
        {
            if (log.isDebugEnabled())
                log.debug("Authentication failed: Authorization header missing.");

            return false;
        }
        else
        {
            String key = SipUtils.getKeyToLocationService(request);
            String username = locationService.getUsername(key);
            String password = locationService.getPassword(key);
            if (password == null) password = "";

            String username_h = authorizationHeader.getParameter("username");
            if (username_h == null) return false;

            if (username_h.indexOf('@') != -1) username_h = username_h.substring(0, username_h.indexOf('@'));

            // If user names are not equal, authorization failed
            if (!username.equals(username_h)) return false;

            return dsam.doAuthenticate(request, authorizationHeader, username_h, password);
        }
    }

    /**
     * Returns whether this service is serving the specified domain
     * @param domain Domain name
     * @return true/false
     */
    public boolean isDomainServed(String domain)
    {
        return domains.contains(domain);
    }
    
    /**
     * Returns whether <i>addr</i> matches any interface's IP or hostname.
     * @param addr Address. Can be IP or hostname.
     * @return true if <i>addr</i> matches any interface's IP or hostname.
     */
    private boolean addrMatchesInterface(String addr)
    {
        return ipAndHostNames.contains(addr);
    }

    /**
     * @see openjsip.remote.RemoteServiceInterface
     */
    public String execCmd(String cmd, String[] parameters) throws RemoteException
    {
        if (cmd == null)
            return null;

        if (cmd.equalsIgnoreCase("get") && parameters != null)
        {
            if (parameters.length > 0)
            {
                if (parameters[0].equalsIgnoreCase("numIncReqs"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_INCOMING_REQUESTS).toString();
                else if (parameters[0].equalsIgnoreCase("numResOK"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_RESPONSES_OK).toString();
                else if (parameters[0].equalsIgnoreCase("numReqRejects"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_REQUEST_REJECTS).toString();
                else if (parameters[0].equalsIgnoreCase("numReqIntErrors"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_REQUEST_PROCESSING_ERRORS).toString();
                else if (parameters[0].equalsIgnoreCase("numAuthFails"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_AUTH_FAILED).toString();
                else if (parameters[0].equalsIgnoreCase("numCancels"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_BINDING_CANCELS).toString();
                else if (parameters[0].equalsIgnoreCase("numCompleteCancels"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_BINDING_FULL_CANCELS).toString();
                else if (parameters[0].equalsIgnoreCase("numQueries"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_BINDING_QUERIES).toString();
                else if (parameters[0].equalsIgnoreCase("numUpdates"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_BINDING_UPDATES).toString();
                else if (parameters[0].equalsIgnoreCase("numNotFound"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_USER_NOT_FOUND).toString();

                else if (parameters[0].equalsIgnoreCase("vm_freememory"))
                    return Long.toString(Runtime.getRuntime().freeMemory());
                else if (parameters[0].equalsIgnoreCase("vm_maxmemory"))
                    return Long.toString(Runtime.getRuntime().maxMemory());
                else if (parameters[0].equalsIgnoreCase("vm_totalmemory"))
                    return Long.toString(Runtime.getRuntime().totalMemory());
                else
                    return null;
            }
        }

        // Return help
        return
        "help                           - Show help.\n" +
        "get numIncReqs                 - Get the total number of incoming requests.\n"+
        "get numResOK                   - Get the total number of successfully processed requests.\n"+
        "get numReqRejects              - Get the total number of rejected requests.\n"+
        "get numReqIntErrors            - Get the total number of dropped requests due to internal errors.\n"+
        "get numAuthFails               - Get the total number of unsuccessfull authentications.\n"+
        "get numCancels                 - Get the total number of bindings being canceled.\n"+
        "get numCompleteCancels         - Get the total number of \"wildcard\" cancels.\n"+
        "get numQueries                 - Get the total number of bindings list requests.\n"+
        "get numUpdates                 - Get the total number of updated bindings.\n"+
        "get numNotFound                - Get the total number of rejected requests because of subscriber was not found in location service database.\n"+
        "get vm_freememory              - Get the amount of free memory in the Java Virtual Machine.\n"+
        "get vm_maxmemory               - Get the maximum amount of memory that the Java virtual machine will attempt to use.\n"+
        "get vm_totalmemory             - Get the total amount of memory in the Java virtual machine.\n";
    }

    /**
     * @see openjsip.remote.RemoteServiceInterface
     */
    public boolean isAlive() throws RemoteException
    {
        return true;
    }

}
