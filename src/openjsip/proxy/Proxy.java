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
package openjsip.proxy;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.NDC;

import javax.sip.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.sip.header.*;
import javax.sip.address.AddressFactory;
import javax.sip.address.Address;
import javax.sip.address.URI;
import javax.sip.address.SipURI;
import java.util.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.NotBoundException;
import java.rmi.Naming;
import java.rmi.server.UnicastRemoteObject;
import java.text.ParseException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;

import openjsip.remote.locationservice.UserNotFoundException;
import openjsip.remote.locationservice.LocationServiceInterface;
import openjsip.remote.RemoteServiceInterface;
import openjsip.SipUtils;
import openjsip.snmp.SNMPAssistant;
import openjsip.auth.DigestServerAuthenticationMethod;
import openjsip.proxy.plugins.MethodPlugin;
import openjsip.proxy.plugins.MethodPluginException;
import gov.nist.javax.sip.stack.SIPServerTransaction;
import gov.nist.javax.sip.message.SIPResponse;
import snmp.*;

public class Proxy extends UnicastRemoteObject implements SipListener, RemoteServiceInterface, Runnable
{
    /**
     * Logger
     */
    private static Logger log = Logger.getLogger(Proxy.class);

    /**
     * Main SIP stack
     */
    private SipStack sipStack;

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
     * Method plugins, such as REGISTER
     */
    private final Hashtable<String, MethodPlugin> methodPlugins = new Hashtable<String, MethodPlugin>();

    /**
     * SipProvider to ip address mapping
     * SipProvider is an interface, and Address is its IP address
     */
    private final Hashtable<SipProvider, String> providerToAddressMapping = new Hashtable<SipProvider, String>();

    /**
     * SipProvider to hostname mapping
     * SipProvider is an interface, and Hostname is its FQDN ( Fully qualified domain name )
     */
    private final Hashtable<SipProvider, String> providerToHostnameMapping = new Hashtable<SipProvider, String>();

    /**
     * Location service connection variables
     */
    private String locationServiceName;
    private String locationServiceHost;
    private int locationServicePort = 1099;
    
    /**
     * See RFC3261 for Timer C details
     */
    private int timercPeriod = 3 * 60 * 1000 + 1000;

    /**
     *  Authenticate subscribers ?
     */
    private boolean authenticationEnabled;

    /**
     * Digest authentication class
     */
    private DigestServerAuthenticationMethod dsam;

    /**
     * Operation mode
     */
    public static final int STATEFULL_MODE = 0;
    public static final int STATELESS_MODE = 1;

    /**
     * Not implemented yet
     */
    private final int operationMode;

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
    protected static final String SNMP_ROOT_OID = "1.3.6.1.4.1.1937.3.";

    protected static final String SNMP_OID_NUM_REQUESTS_PROCESSED = SNMP_ROOT_OID + "1.1";
    protected static final String SNMP_OID_NUM_RESPONSES_PROCESSED = SNMP_ROOT_OID + "1.2";
    protected static final String SNMP_OID_NUM_REQUEST_PROCESSING_ERRORS = SNMP_ROOT_OID + "1.3";
    protected static final String SNMP_OID_NUM_RESPONSE_PROCESSING_ERRORS = SNMP_ROOT_OID + "1.4";
    protected static final String SNMP_OID_NUM_SERVER_TRANSACTIONS = SNMP_ROOT_OID + "1.5";
    protected static final String SNMP_OID_NUM_CLIENT_TRANSACTIONS = SNMP_ROOT_OID + "1.6";

    /**
     * SNMP database with default values.
     */
    private static final Object SNMP_DATABASE[][] = new Object[][]
    {
        { SNMP_OID_NUM_REQUESTS_PROCESSED, new SNMPCounter32(0) },
        { SNMP_OID_NUM_RESPONSES_PROCESSED, new SNMPCounter32(0) },
        { SNMP_OID_NUM_REQUEST_PROCESSING_ERRORS, new SNMPCounter32(0) },
        { SNMP_OID_NUM_RESPONSE_PROCESSING_ERRORS, new SNMPCounter32(0) },
        { SNMP_OID_NUM_SERVER_TRANSACTIONS, new SNMPGauge32(0) },
        { SNMP_OID_NUM_CLIENT_TRANSACTIONS, new SNMPGauge32(0) },
    };


    /**
     * Entry point
     *
     * @param args command line arguments
     */
    public static void main(String[] args)
    {
        Properties props = null;

        if (args.length < 1)
        {
            printUsage();
            System.exit(0);
        }

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
            System.err.println("Error: Cannot open configuration file "+args[0]);
            System.exit(1);
        }


        RemoteServiceInterface proxy = null;

        try
        {
            // Start proxy
            proxy = new Proxy(props);
        }       
        catch (Exception ex)
        {
            log.error(ex.getMessage());
            if (log.isTraceEnabled())
                log.trace("", ex);
            
            System.exit(1);
        }


        String name = props.getProperty("proxy.service.rmi.objectname", "Proxy").trim();
        String host = props.getProperty("proxy.service.rmi.host", "localhost").trim();
        int port = 1099;

        try
        {
            port = Integer.parseInt(props.getProperty("proxy.rmi.port", "1099").trim());
        }
        catch(NumberFormatException ex)
        {
            // ignored
        }

        RMIBindName = "rmi://" + host + ":" + port + "/" + name;

        try
        {
            Naming.rebind(RMIBindName, proxy);
        }
        catch(RemoteException ex)
        {
            log.error("Cannot register within RMI registry at "+host+":"+port, ex);
            System.exit(1);
        }
        catch(MalformedURLException ex)
        {
            log.error("Cannot register within RMI registry at "+host+":"+port, ex);
            System.exit(1);
        }

        if (log.isInfoEnabled())
            log.info("Proxy registered as \""+name+"\" within RMI registry at "+host+":"+port);

        if (log.isInfoEnabled())
            log.info("Proxy started...");
    }

    /**
     * Prints help on how to launch this program
     */
    private static void printUsage()
    {
        System.out.println("\nUsage: Proxy <proxy.properties file>\n" +
                           "where proxy.properties is the path to .properties file with settings for Proxy server.");
    }

    /**
     * Proxy constructor
     * @param props Proxy server configuration
     * @throws PeerUnavailableException
     * @throws ObjectInUseException
     * @throws TooManyListenersException
     * @throws TransportNotSupportedException
     * @throws InvalidArgumentException
     */
    private Proxy(Properties props) throws PeerUnavailableException, ObjectInUseException, TooManyListenersException,
                                           TransportNotSupportedException, InvalidArgumentException, RemoteException
    {                                                                                                               
        if (log.isInfoEnabled())
            log.info("Starting Proxy v" + SipUtils.OPENJSIP_VERSION + "...");

        // Set security manager
        if (System.getSecurityManager() == null)
            System.setSecurityManager(new SecurityManager());

        SipFactory sipFactory = SipFactory.getInstance();

        sipFactory.setPathName("gov.nist");

        props.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");

        sipStack = sipFactory.createSipStack(props);

        headerFactory = sipFactory.createHeaderFactory();

        addressFactory = sipFactory.createAddressFactory();

        messageFactory = sipFactory.createMessageFactory();


        if (log.isInfoEnabled())
            log.info("Configuring interfaces...");

        try
        {
            // If there are no configured interfaces
            if (props.getProperty("proxy.interface.1.addr") == null)
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
                        props.setProperty("proxy.interface."+index+".addr", inetAddress.getHostAddress());
                        index++;
                    }
                }

            }
        }
        catch (SocketException e)
        {

        }

        int index = 1;                                  
        String proxyHost = null;

        while ((proxyHost = props.getProperty("proxy.interface."+index+".addr")) != null)
        {
            proxyHost = proxyHost.trim();

            try
            {
                if (log.isTraceEnabled())
                    log.trace("Configuring interface #"+index+": "+proxyHost);
                
                // Get IP address of interface
                InetAddress inetAddress = InetAddress.getByName(proxyHost);
                // Get desired port
                String proxyPortStr = props.getProperty("proxy.interface."+index+".port");
                if (proxyPortStr != null) proxyPortStr = proxyPortStr.trim();

                int proxyPort = 5060;
                try
                {
                    proxyPort = Integer.parseInt(proxyPortStr);
                }
                catch (NumberFormatException e)
                {

                }
                // Get list of transports
                String[] transports = props.getProperty("proxy.interface."+index+".transport", "udp, tcp").split(",");

                // SipProvider represents an interface
                SipProvider sipProvider = null;

                // Transport is represented by ListeningPoint
                for (int i=0; i<transports.length; i++)
                {
                    transports[i] = transports[i].trim();

                    try
                    {
                        if (log.isTraceEnabled())
                            log.trace("Creating ListeningPoint for " + inetAddress.getHostAddress() + ":" + proxyPort + " " + transports[i]);

                        // Try to create ListeningPoint for each transport
                        ListeningPoint lp = sipStack.createListeningPoint(inetAddress.getHostAddress(), proxyPort, transports[i]);

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
                        log.warn("Failed to create listening point " + inetAddress.getHostAddress() + ":" + proxyPort + " " + transports[i] +" ( "+ex.getMessage()+" )");
                        if (log.isTraceEnabled())
                            log.trace("", ex);
                    }
                }

                // If interface has any ListeningPoints
                if (sipProvider != null && sipProvider.getListeningPoints().length > 0)
                {
                    providerToAddressMapping.put(sipProvider, inetAddress.getHostAddress());
                    providerToHostnameMapping.put(sipProvider, inetAddress.getCanonicalHostName());

                    sipProvider.addSipListener(this);
                }
            }
            catch (UnknownHostException ex)
            {
                log.warn("Interface #"+index+": "+ex.getMessage());
            }
            finally
            {
                index++;
            }
        }

        if (providerToHostnameMapping.size() == 0)
        {
            log.error("There are no properly configured interfaces. Proxy cannot be started.");
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
            sb.append("Interface #" + index + ": " + getHostname(sipProvider) + " (");

            for (int i=0; i<lps.length; i++)
            {
                if (i == 0)
                    sb.append(lps[i].getIPAddress()+") via ");
                sb.append(lps[i].getTransport());

                if (i < lps.length -1)
                    sb.append(", ");
            }

            if (log.isInfoEnabled())
                log.info(sb.toString());

            index++;
        }


        locationServiceName = props.getProperty("proxy.location.service.rmi.objectname", "LocationService").trim();
        locationServiceHost = props.getProperty("proxy.location.service.rmi.host", "localhost").trim();

        try
        {
            locationServicePort = Integer.parseInt(props.getProperty("proxy.location.service.rmi.port", "1099").trim());
        }
        catch(NumberFormatException ex)
        {
            // ignored
        }

        if (log.isInfoEnabled())
            log.info("Connecting to Location Service server at " + locationServiceHost + ":" + locationServicePort + " ...");

        LocationServiceInterface locationService = getLocationService();
        if (locationService != null && locationService.isAlive())
        {
            if (log.isInfoEnabled())
                log.info("Successfully connected.");
        }
        else
            throw new RemoteException("Cannot connect to Location Service server.");

        /**
         * Reading domain configuration
         */
        String domainsStr = props.getProperty("proxy.domains");
        if (domainsStr != null)
        {
            String[] domainsArray = domainsStr.trim().split(",");

            for (String domain : domainsArray)
            {
                domain = domain.trim();
                if (domain.length() > 0) domains.add(domain);
            }
        }

        if (domains.isEmpty())
        {
            log.warn("No domains configured. Retreiving the domain list from Location Service...");

            domains.addAll(locationService.getDomains());
        }

        if (domains.isEmpty())
        {
            log.error("No domains configured. Proxy cannot be started.");
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

            log.info("Proxy is responsible for domains: " + sb.toString());
        }

        authenticationEnabled = props.getProperty("proxy.authentication.enabled", "no").trim().equalsIgnoreCase("yes");

        if (log.isInfoEnabled())
        {
            if (authenticationEnabled)
                log.info("Authentication enabled.");
            else
                log.info("Authentication disabled.");
        }

        String operationModeStr = props.getProperty("proxy.operation.mode", "stateless").trim().toLowerCase();
        if (operationModeStr.equals("statefull"))
            operationMode = STATEFULL_MODE;
        else
            operationMode = STATELESS_MODE;
            

        if (log.isInfoEnabled())
        {
            if (operationMode == STATEFULL_MODE)
                log.info("Proxy operation mode: statefull.");
            else if (operationMode == STATELESS_MODE)
                log.info("Proxy operation mode: stateless.");
            else
                log.info("Proxy operation mode: unknown.");
        }

        try
        {
            dsam = new DigestServerAuthenticationMethod(domains.iterator().next(), new String[] { "MD5" });
        }
        catch (NoSuchAlgorithmException ex)
        {
            log.error("Cannot create authentication method. Some algorithm is not implemented: " + ex.getMessage());
            if (log.isTraceEnabled())
                log.trace(null, ex);

            System.exit(1);
        }

        /**
         * It's time to load method plugins (REGISTER for example).
         */
        if (log.isInfoEnabled())
            log.info("Loading method plugins...");

        index = 1;
        String pluginClass;

        while ((pluginClass = props.getProperty("proxy.method.plugin." + index + ".classname")) != null)
        {
            pluginClass = pluginClass.trim();

            String pluginEnabled = props.getProperty("proxy.method.plugin." + index + ".enabled", "true");
            if (!pluginEnabled.equals("true") && !pluginEnabled.equals("yes"))
            {
                index++;
                continue;
            }

            if (log.isInfoEnabled())
                log.info("Loading "+pluginClass);

            try
            {
                Class c = Class.forName(pluginClass);
                MethodPlugin methodPlugin = (MethodPlugin) c.newInstance();

                String pathToPropertiesFile = props.getProperty("proxy.method.plugin." + index + ".properties");
                Properties pluginProperties = props;

                if (pathToPropertiesFile != null)
                {
                    pluginProperties = new Properties();
                    pluginProperties.load(new FileInputStream(pathToPropertiesFile.trim()));
                }

                methodPlugin.initialize(pluginProperties, this);

                if (methodPlugin.isInitialized())
                    methodPlugins.put(methodPlugin.getMethod(), methodPlugin);
            }
            catch (ClassNotFoundException ex)
            {
                log.error("Cannot load plugin class.", ex);
            }
            catch (InstantiationException ex)
            {
                log.error("Cannot load plugin class.", ex);
            }
            catch (IllegalAccessException ex)
            {
                log.error("Cannot load plugin class.", ex);
            }
            catch (IOException ex)
            {
                log.error("Cannot load .properties file. "+ ex.getMessage());
            }
            catch (Exception ex)
            {
                log.error("Plugin failed to initialize. "+ex.getMessage());
            }

            index++;
        }

        /**
         * Read SNMP configuration
         */
        boolean isSnmpEnabled = props.getProperty("proxy.snmp.agent.enabled", "yes").trim().equalsIgnoreCase("yes");
        if (isSnmpEnabled)
        {
            int snmpPort = 1163;

            try
            {
                snmpPort = Integer.parseInt(props.getProperty("proxy.snmp.agent.port", "1163").trim());
            }
            catch (NumberFormatException e)
            {
                /* ignored */
            }

            String communityName = props.getProperty("proxy.snmp.agent.community", "public").trim();

            // Create our assistant class. This class should not be null even if SNMP is not enabled.
            snmpAssistant = new SNMPAssistant(communityName, SNMP_DATABASE);

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
            Naming.unbind(RMIBindName);
        }
        catch (Exception e)
        {
            /* ignored */
        }
    }


    /**
     * Returns remote Location Service instance. Do not cache this instance,
     * because once Location Service restarted, it cannot be contacted without reconnecting.
     * @return Remote Location Service instance.
     */
    public LocationServiceInterface getLocationService()
    {
        try
        {
            Registry registry = LocateRegistry.getRegistry(locationServiceHost, locationServicePort);
            LocationServiceInterface locationService = (LocationServiceInterface) registry.lookup(locationServiceName);
            return locationService;
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
     * @return SNMP assistant.
     */
    public SNMPAssistant getSnmpAssistant()
    {
        return snmpAssistant;
    }


    public void processRequest(RequestEvent requestEvent)
    {
        Request request = requestEvent.getRequest();
        CallIdHeader callidHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);

        // Place Call-ID header to each log message
        NDC.push(callidHeader != null ? callidHeader.getCallId() : Long.toString(System.currentTimeMillis()));

        try
        {
            processIncomingRequest(requestEvent);

            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_REQUESTS_PROCESSED);
        }
        catch (Exception ex)
        {
            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_REQUEST_PROCESSING_ERRORS);


            if (log.isDebugEnabled())
                log.debug("Exception: " + ex.getMessage());
            if (log.isTraceEnabled())
                log.trace("Exception dump: ", ex);
        }

        NDC.remove();
    }


    public void processResponse(ResponseEvent responseEvent)
    {
        Response response = responseEvent.getResponse();
        CallIdHeader callidHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);

        // Place Call-ID header to each log message
        NDC.push(callidHeader != null ? callidHeader.getCallId() : Long.toString(System.currentTimeMillis()));

        try
        {
            processIncomingResponse(responseEvent);
            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_RESPONSES_PROCESSED);
        }
        catch (Exception ex)
        {
            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_RESPONSE_PROCESSING_ERRORS);

            if (log.isDebugEnabled())
                log.debug("Exception: " + ex.getMessage());
            if (log.isTraceEnabled())
                log.trace("Exception dump: ", ex);
        }

        NDC.remove();
    }


    public void processTimeout(TimeoutEvent event)
    {
        ClientTransaction clientTransaction = event.getClientTransaction();

        if (clientTransaction != null)
        {
            TransactionsMapping transactionsMapping = (TransactionsMapping) clientTransaction.getApplicationData();
            if (transactionsMapping != null)
            {
                transactionsMapping.cancelTimerC(clientTransaction);
                checkResponseContext(transactionsMapping);
            }
        }

        if (log.isTraceEnabled())
            log.trace("Timeout occured at "+getHostname((SipProvider) event.getSource())+". CT = "+event.getClientTransaction()+"  ST = "+event.getServerTransaction());
    }


    public void processIOException(IOExceptionEvent event)
    {
        if (log.isTraceEnabled())
            log.trace("IOException occured at "+getHostname((SipProvider) event.getSource())+". Host = "+event.getHost()+"  Port = "+event.getPort()+" Transport = "+event.getTransport());
    }

    
    public void processTransactionTerminated(TransactionTerminatedEvent event)
    {
        if (event.isServerTransaction())
            snmpAssistant.decrementSnmpInteger(SNMP_OID_NUM_SERVER_TRANSACTIONS);
        else
            snmpAssistant.decrementSnmpInteger(SNMP_OID_NUM_CLIENT_TRANSACTIONS);

        if (log.isTraceEnabled())
            log.trace("Transaction terminated at "+getHostname((SipProvider) event.getSource())+". CT = "+event.getClientTransaction()+"  ST = "+event.getServerTransaction());

        ClientTransaction clientTransaction = event.getClientTransaction();

        if (clientTransaction != null)
        {
            TransactionsMapping transactionsMapping = (TransactionsMapping) clientTransaction.getApplicationData();
            if (transactionsMapping != null)
            {
                transactionsMapping.cancelTimerC(clientTransaction);
                checkResponseContext(transactionsMapping);
            }
        }
    }

    /**
     * Cannot be called in proxies
     * @param event DialogTerminatedEvent object
     */
    public void processDialogTerminated(DialogTerminatedEvent event)
    {
        
    }

    /**
     * Returns whether proxy is responsible for domain
     * @param domain Domain name. Cannot be IP or hostname. See addrMatchesInterface() function instead.
     * @return true if proxy is responsible for <i>domain</i>, false otherwise.
     */
    public boolean isDomainServed(String domain)
    {
        return domains.contains(domain);
    }

    public Iterator getSipProviders()
    {
        return sipStack.getSipProviders();
    }

    public HashSet getDomains()
    {
        return domains;
    }

    public String getDefaultDomain()
    {
        return domains.iterator().next();
    }

    public int getOperationMode()
    {
        return operationMode;
    }

    /**
     * Returns whether <i>addr</i> matches any interface's IP or hostname.
     * @param addr Address. Can be IP or hostname.
     * @return true if <i>addr</i> matches any interface's IP or hostname.
     */
    public boolean addrMatchesInterface(String addr)
    {
        return getProviderByAddr(addr) != null;
    }

    /**
     * Returns SipProvider instance, whose associated network interface IP or hostname equals <i>addr</i>
     * @param addr Address. Can be IP or hostname.
     * @return returns SipProvider instance, whose associated network interface IP or hostname equals <i>addr</i>
     */
    public SipProvider getProviderByAddr(String addr)
    {
        Iterator iterator = sipStack.getSipProviders();

        while (iterator.hasNext())
        {
            SipProvider sipProvider = (SipProvider) iterator.next();
            if (addr.equals(getIPAddress(sipProvider))) return sipProvider;
            if (addr.equals(getHostname(sipProvider))) return sipProvider;
        }

        return null;
    }


    /**
     * Returns the IP address of proxy interface
     * @param sipProvider SipProvider object
     * @return IP address
     */
    public String getIPAddress(SipProvider sipProvider)
    {
        return providerToAddressMapping.get(sipProvider);
    }

    /**
     * Returns the FQDN of proxy interface
     * @param sipProvider SipProvider object
     * @return FQDN
     */
    public String getHostname(SipProvider sipProvider)
    {
        return providerToHostnameMapping.get(sipProvider);
    }

    /**
     * Processes incoming requests and forwards them if necessary
     * @param requestEvent Request event
     * @throws InvalidArgumentException
     * @throws ParseException
     * @throws SipException
     */
    private void processIncomingRequest(final RequestEvent requestEvent) throws InvalidArgumentException, ParseException, SipException
    {
        Request request = requestEvent.getRequest();
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        ServerTransaction serverTransaction = (operationMode == STATEFULL_MODE ? requestEvent.getServerTransaction() : null);
        String method = request.getMethod();

        if (log.isDebugEnabled())
        {
            log.debug("-------------------");
            log.debug("Incoming "+method+" request "+request.getRequestURI().toString().trim());
        }

        if (log.isTraceEnabled())
            log.trace("\n"+request.toString());

        /**
         * Get location service interface
         */
        LocationServiceInterface locationService = getLocationService();
        if (locationService == null)
        {
            log.error("Cannot connect to Location Service server. Check if server is running and registered within RMI registry at target host.");        
            
            SipUtils.sendResponse(Response.SERVER_INTERNAL_ERROR, sipProvider, messageFactory, request, serverTransaction);
            return;
        }


        /**
         *  For all new requests, including any with unknown methods, an element
         *  intending to proxy the request MUST:
         *
         *  1. Validate the request (Section 16.3)
         */
        if (!validateRequest(request, sipProvider, serverTransaction, locationService))
        {
            if (log.isDebugEnabled())
                log.debug("Request is not valid.");
            return;
        }        

        /**
         *  For all new requests, including any with unknown methods, an element
         *  intending to proxy the request MUST:
         *
         *  ...

         *  2. Preprocess routing information (Section 16.4)
         */

        // Get Request-URI line
        URI requestURI = request.getRequestURI();

        // Check once and for all, is it SIP URI or not ?
        SipURI requestSipURI = requestURI.isSipURI() ? (SipURI) requestURI : null;

        /**
         * The proxy MUST inspect the Request-URI of the request. If the
         * Request-URI of the request contains a value this proxy previously
         * placed into a Record-Route header field (see Section 16.6 item
         * 4), the proxy MUST replace the Request-URI in the request with
         * the last value from the Route header field, and remove that value
         * from the Route header field. The proxy MUST then proceed as if it
         * received this modified request.
         *
         *   This will only happen when the element sending the request to the
         *   proxy (which may have been an endpoint) is a strict router.
         */
        if (log.isTraceEnabled())
            log.trace("Inspecting request for Strict Routing mechanism...");

        ListIterator routes = request.getHeaders(RouteHeader.NAME);
        if (routes != null && routes.hasNext())
        {
            ListeningPoint[] lps = sipProvider.getListeningPoints();

            SipURI sipURI_form1 = addressFactory.createSipURI(null, getIPAddress(sipProvider));
            sipURI_form1.setPort(lps[0].getPort());
            SipURI sipURI_form2 = addressFactory.createSipURI(null, getHostname(sipProvider));
            sipURI_form2.setPort(lps[0].getPort());

            if (requestURI.equals(sipURI_form1) || requestURI.equals(sipURI_form2))
            {
                RouteHeader lastRouteHeader;

                // Get to the last value
                do lastRouteHeader = (RouteHeader) routes.next();
                while (routes.hasNext());

                request.setRequestURI(lastRouteHeader.getAddress().getURI());
                request.removeLast(RouteHeader.NAME);

                if (log.isDebugEnabled())
                    log.debug("Strict routing detected ! Request was modified and dispatched again for processing.");

                if (method.equals("BYE"))
                {
                    int a = 5;
                }

                Thread thread = new Thread()
                {
                    public void run()
                    {
                        processRequest(requestEvent);
                    }
                };

                thread.start();
                return;
            }

            if (log.isTraceEnabled())
                log.trace("Strict routing was not detected, Request-URI is not this proxy");
        }
        else if (log.isTraceEnabled())
            log.trace("Strict routing was not detected, because there are no Route headers.");

        /**
         * If the Request-URI contains a maddr parameter, the proxy MUST
         * check to see if its value is in the set of addresses or domains
         * the proxy is configured to be responsible for. If the Request-URI
         * has a maddr parameter with a value the proxy is responsible for,
         * and the request was received using the port and transport
         * indicated (explicitly or by default) in the Request-URI, the
         * proxy MUST strip the maddr and any non-default port or transport
         * parameter and continue processing as if those values had not been
         * present in the request.
         */
        if (requestSipURI != null)
        {
            if (requestSipURI.getMAddrParam() != null)
            {
                if (log.isTraceEnabled())
                    log.trace("Request contains maddr parameter "+requestSipURI.getMAddrParam()+".");

                if (isDomainServed(requestSipURI.getMAddrParam()) || getProviderByAddr(requestSipURI.getMAddrParam()) != null)
                {
                    String transport = requestSipURI.getTransportParam();
                    if (transport == null) transport = "udp";

                    int port = requestSipURI.getPort();
                    if (port == -1) port = 5060;

                    /**
                     * The current JAIN-SIP API doesn't have methods to check via which ListeningPoint request was received.
                     * So we make some silly check
                     */

                    ListeningPoint lp = sipProvider.getListeningPoint(transport);
                    if (lp != null && port == lp.getPort())
                    {
                        if (log.isTraceEnabled())
                            log.trace("The maddr contains a domain "+requestSipURI.getMAddrParam()+" we are responsible for,"
                                      + " we remove the mAddr, non-default port and transport parameters from the original request");

                            // We have to strip the madr parameter:
                            requestSipURI.removeParameter("maddr");
                            // We have to strip the non-default port parameter:
                            if (requestSipURI.getPort() != 5060 && requestSipURI.getPort() != -1)
                                requestSipURI.removeParameter("port");

                        requestSipURI.removeParameter("transport");
                    }
                    else
                    if (log.isTraceEnabled())
                        log.trace("The maddr contains a domain "+requestSipURI.getMAddrParam()+" we are responsible for,"
                                  + " but listening point for specified port and transport cannot be found.");
                }
                else
                {
                    // The Maddr parameter is not a domain we have to take
                    // care of, we pass this check...
                    if (log.isTraceEnabled())
                        log.trace("Maddr parameter "+requestSipURI.getMAddrParam()+" is not domain we have to take care. Continue processing the request.");
                }
            }
            else
            {
                if (log.isTraceEnabled())
                    log.trace("Request doesn't contain maddr parameter.");
            }
        }
        else
        {
            // No SipURI, so no Maddr parameter, we pass this check...
            if (log.isTraceEnabled())
                log.trace("RequestURI "+requestURI+" is not SIP URI. Continue processing the request.");
        }

        /**
         * If the first value in the Route header field indicates this proxy,
         * the proxy MUST remove that value from the request.
         */
        routes = request.getHeaders(RouteHeader.NAME);
        if (routes != null && routes.hasNext())
        {
            RouteHeader routeHeader = (RouteHeader) routes.next();
            Address routeAddress = routeHeader.getAddress();
            URI routeURI = routeAddress.getURI();
            if (routeURI.isSipURI())
            {
                SipURI routeSipURI = (SipURI) routeURI;

                // We need only host and port
                String routeHost = routeSipURI.getHost();
                int routePort = routeSipURI.getPort();
                // Port may be absent
                if (routePort == -1) routePort = 5060;

                ListeningPoint[] lps = sipProvider.getListeningPoints();
                if ( addrMatchesInterface(routeHost)  && routePort == lps[0].getPort() )
                {
                    if (log.isTraceEnabled())
                        log.trace("Removing the first route "+routeSipURI+" from the RouteHeader: matches the proxy "+routeHost+":"+routePort);

                    request.removeFirst(RouteHeader.NAME);
                }
            }
            else if (log.isTraceEnabled())
                log.trace("Route Header value " + routeURI+" is not a SIP URI");
        }
        else  if (log.isTraceEnabled())
            log.trace("Request doesn't contain Route header.");


        /**
         *  ---------------- Route preprocessing is done. ------------------
         */

        /**
         * A stateful proxy has a server transaction associated with one or more
         * client transactions by a higher layer proxy processing component
         * (see figure 3), known as a proxy core.  An incoming request is processed
         * by a server transaction.
         * ...
         * A stateful proxy creates a new server transaction for each new request received.
         */

        /**
         * Note: not all requests are handled by server transactions. So serverTransaction can be null.
         */
        serverTransaction = (operationMode == STATEFULL_MODE ? checkServerTransaction(sipProvider, request, serverTransaction) : null);

        if (log.isTraceEnabled())
            log.trace("Server transaction for request is: "+serverTransaction);


        /**
         *  For all new requests, including any with unknown methods, an element
         *  intending to proxy the request MUST:
         *
         *  ...
         *  3. Determine target(s) for the request (Section 16.5)
         *
         */

        /**
         * 16.5 Determining Request Targets
         *
         * Next, the proxy calculates the target(s) of the request.  The set of
         * targets will either be predetermined by the contents of the request
         * or will be obtained from an abstract location service.  Each target
         * in the set is represented as a URI.
         */

        /**
         * If the Request-URI of the request contains an maddr parameter,
         * the Request-URI MUST be placed into the target set as the only
         * target URI, and the proxy MUST proceed to Section 16.6.
         */
        if (log.isTraceEnabled())
            log.trace("Checking if Request-URI contains an maddr parameter...");

        if (requestSipURI != null && requestSipURI.getMAddrParam() != null)
        {
            if (log.isTraceEnabled())
                log.trace("The only target is the Request-URI (mAddr parameter). Forwarding request.");

            forwardRequest(requestURI, request, sipProvider, serverTransaction, serverTransaction != null);
            return;
        }

        if (log.isTraceEnabled())
            log.trace("No, Request-URI is not SIP URI or doesn't contain an maddr parameter.");

        /**
         * If the domain of the Request-URI indicates a domain this element
         * is not responsible for, the Request-URI MUST be placed into the
         * target set as the only target, and the element MUST proceed to
         * the task of Request Forwarding (Section 16.6).
         */
        if (log.isTraceEnabled())
            log.trace("Checking if proxy is responsible for the domain...");

        if (requestSipURI != null)
        {
            if (!isDomainServed(requestSipURI.getHost()) && !addrMatchesInterface(requestSipURI.getHost()))
            {
                if (log.isTraceEnabled())
                    log.trace("No, so forwarding request...");

                forwardRequest(requestURI, request, sipProvider, serverTransaction, serverTransaction != null);
                return;
            }
        }

        if (log.isTraceEnabled())
            log.trace("Yes, proxy is responsible for the domain.");


        if (log.isTraceEnabled())
            log.trace("Handling special requests like CANCEL and REGISTER...");

        // Flag that says that this request MUST be processed statelessly
        // Usage is below.
        boolean requestMustBeProcessedStatelessly = false;

        /**
         * Stateless proxies MUST NOT perform special processing for CANCEL requests.
         */
        if (operationMode == STATEFULL_MODE && method.equals(Request.CANCEL))
        {
            /**
             * ...the proxy layer searches its existing response contexts for
             * the server transaction handling the request associated with this
             * CANCEL.
             */
            SIPServerTransaction sipServerTransaction = (SIPServerTransaction) serverTransaction;
            /**
             * It was easier than I thought.
             */
            SIPServerTransaction serverTransactionToTerminate = sipServerTransaction.getCanceledInviteTransaction();

            if (serverTransactionToTerminate != null)
            {
                // Transaction mapping is the response context itself
                TransactionsMapping transactionsMapping = (TransactionsMapping) serverTransactionToTerminate.getApplicationData();
                if (transactionsMapping != null)
                {
                    /**
                     * If a matching response context is found, the element MUST
                     * immediately return a 200 (OK) response to the CANCEL request.  In
                     * this case, the element is acting as a user agent server as defined in
                     * Section 8.2.
                     */
                    Response okResponse = messageFactory.createResponse(Response.OK, request);
                    serverTransaction.sendResponse(okResponse);

                    /**
                     * Furthermore, the element MUST generate CANCEL requests
                     * for all pending client transactions in the context as described in
                     * Section 16.7 step 10.
                     */
                    if (log.isTraceEnabled())
                        log.trace("OK replied back. \n"+okResponse+"\nTerminating pending ClientTransactions.");
                    if (log.isDebugEnabled())
                        log.debug("OK replied back.");

                    ClientTransaction[] clientTransactions = transactionsMapping.getClientTransactionsArray();
                    cancelPendingTransactions(clientTransactions, sipProvider);

                    return;
                }
            }

            /**
             * If a response context is not found, the element does not have any
             * knowledge of the request to apply the CANCEL to.  It MUST statelessly
             * forward the CANCEL request (it may have statelessly forwarded the
             * associated request previously).
             */
            requestMustBeProcessedStatelessly = true;
        }


        MethodPlugin methodPlugin = methodPlugins.get(request.getMethod());
        if (methodPlugin != null)
        {
            if (log.isDebugEnabled())
                log.debug("Processing request via "+methodPlugin.getClass()+" method plugin.");

            try
            {
                Response response = methodPlugin.processRequest(request);

                if (response != null)
                {
                    if (serverTransaction != null)
                        serverTransaction.sendResponse(response);
                    else
                        sipProvider.sendResponse(response);

                    if (log.isDebugEnabled())
                        log.debug("Replied "+response.getReasonPhrase()+" ("+response.getStatusCode()+")");

                    if (log.isTraceEnabled())
                        log.trace("\n"+response);

                    return;
                }
                else if (log.isDebugEnabled())
                    log.debug("Plugin "+methodPlugin.getClass()+" didn't processed request. Response is NULL. Continue processing...");
            }
            catch(MethodPluginException ex)
            {
                log.warn("Plugin "+methodPlugin.getClass()+" failed to process request. Request dropped. Internal Server Error replied. " + ex.getMessage());
                if (log.isTraceEnabled())
                    log.trace("Plugin " + methodPlugin.getClass() + " failed to process request. Request dropped. Internal Server Error replied. ", ex);

                SipUtils.sendResponse(Response.SERVER_INTERNAL_ERROR, sipProvider, messageFactory, request, serverTransaction);
                return;
            }
        }

        if (log.isTraceEnabled())
            log.trace("This is not special message or it was not processed. ");

        /**
         * If the target set for the request has not been predetermined as
         * described above, this implies that the element is responsible for
         * the domain in the Request-URI, and the element MAY use whatever
         * mechanism it desires to determine where to send the request. ...
         * When accessing the location service constructed by a registrar,
         * the Request-URI MUST first be canonicalized as described in
         * Section 10.3 before being used as an index.
         */

        if (log.isTraceEnabled())
            log.trace("Determining targets for request.");

        Vector<ContactHeader> targetURIList = null;

        // Key is an index into Location Service database
        String key = null;

        // Check if To header doesn't contain proxy IP address instead of domain.
        // If it contains, then we should substitute it with the default domain.
        // Because Location Service is operating in terms of domains, and it should not
        // know anything about interfaces and ip addresses.
        URI toURI = ((ToHeader) request.getHeader(ToHeader.NAME)).getAddress().getURI();
        if (toURI.isSipURI())
        {
            SipURI toSipURI = (SipURI) toURI;
            
            if (addrMatchesInterface(toSipURI.getHost()))
            {
                SipURI fixedURI = (SipURI) toSipURI.clone();
                fixedURI.setHost(getDefaultDomain());
                key = SipUtils.getKeyToLocationService(fixedURI);
            }
        }

        if (key == null)
            key = SipUtils.getKeyToLocationService(request);

        try
        {
            targetURIList = locationService.getContactHeaders(key);
        }
        catch (RemoteException ex)
        {
            SipUtils.sendResponse(Response.SERVER_INTERNAL_ERROR, sipProvider, messageFactory, request, serverTransaction);
            return;
        }
        catch (UserNotFoundException ex)
        {
            if (log.isDebugEnabled())
                log.debug("User " + key + " not found. " + Response.NOT_FOUND + " replied.");

            SipUtils.sendResponse(Response.NOT_FOUND, sipProvider, messageFactory, request, serverTransaction);
            return;
        }


        // If we have target list, the we can fork the request
        if (targetURIList != null && !targetURIList.isEmpty())
        {
            /**if (targetURIList.size() > 1 && !request.getMethod().equals("INVITE"))
             {
             if (log.isTraceEnabled())
             log.trace("The request to fork is not an INVITE, so we will process it with the first target as the only target.");

             ContactHeader ch = (ContactHeader) targetURIList.firstElement();
             targetURIList.removeAllElements();
             targetURIList.addElement(ch);
             }*/

            /**
             *  A stateless proxy MUST follow the request processing steps described
             * in Sections 16.4 through 16.5 with the following exception:
             *
             *      A stateless proxy MUST choose one and only one target from the
             *      target set.  This choice MUST only rely on fields in the
             *      message and time-invariant properties of the server.  In
             *      particular, a retransmitted request MUST be forwarded to the
             *      same destination each time it is processed.  Furthermore,
             *      CANCEL and non-Routed ACK requests MUST generate the same
             *      choice as their associated INVITE.
             */
            if (operationMode == STATELESS_MODE && targetURIList.size() > 1)
            {
                ContactHeader ch = targetURIList.firstElement();
                targetURIList.removeAllElements();
                targetURIList.add(ch);
            }

            /**
             *   4. Forward the request to each target (Section 16.6)
             */
            for( ContactHeader ch : targetURIList)
            {
                URI targetURI = ch.getAddress().getURI();
                forwardRequest(targetURI, request, sipProvider, serverTransaction, serverTransaction != null & !requestMustBeProcessedStatelessly);
            }
        }
        else
        {
            /**
             * If the target set remains empty after applying all of the above, the
             * proxy MUST return an error response, which SHOULD be the 480
             * (Temporarily Unavailable) response.
             */
            SipUtils.sendResponse(Response.TEMPORARILY_UNAVAILABLE, sipProvider, messageFactory, request, serverTransaction);

            if (log.isDebugEnabled())
                log.debug("Target cannot be determined. "+Response.TEMPORARILY_UNAVAILABLE+" ( Temporarily Unavailable ) replied.");
        }
    }

    /**
     * Validates incoming requests. See section 16.3 RFC 3261.
     * Sends error responses if request is not valid.
     * @param request Request to validate
     * @param sipProvider SipProvider object
     * @param serverTransaction Associated server transaction if any
     * @return true - request is valid, false - otherwise (error response is also sent).
     * @throws InvalidArgumentException
     * @throws SipException
     * @throws ParseException
     */
    public boolean validateRequest(Request request, SipProvider sipProvider, ServerTransaction serverTransaction, LocationServiceInterface locationService) throws InvalidArgumentException, SipException, ParseException
    {
        /**
         * 16.3 Request Validation
         *
         * Before an element can proxy a request, it MUST verify the message's
         * validity.  A valid message must pass the following checks:
         *
         *    1. Reasonable Syntax
         *
         *    2. URI scheme
         *
         *    3. Max-Forwards
         *
         *    4. (Optional) Loop Detection
         *
         *    5. Proxy-Require
         *
         *    6. Proxy-Authorization
         *
         * If any of these checks fail, the element MUST behave as a user agent
         * server (see Section 8.2) and respond with an error code.
         *
         */
        String uriScheme = request.getRequestURI().getScheme();

        if (!(uriScheme.equals("sip") || uriScheme.equals("sips") || uriScheme.equals("tel")))
        {
            if (log.isDebugEnabled())
                log.debug("Unsupported URI scheme: "+uriScheme);

            SipUtils.sendResponse(Response.UNSUPPORTED_URI_SCHEME, sipProvider, messageFactory, request, serverTransaction);
            return false;
        }

        /**
         * If the request does not contain a Max-Forwards header field, this
         * check is passed.
         *
         * If the request contains a Max-Forwards header field with a field
         * value greater than zero, the check is passed.
         *
         * If the request contains a Max-Forwards header field with a field
         * value of zero (0), the element MUST NOT forward the request.  If
         * the request was for OPTIONS, the element MAY act as the final
         * recipient and respond per Section 11.  Otherwise, the element MUST
         * return a 483 (Too many hops) response.
         */
        MaxForwardsHeader mf = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);
        if (mf != null && mf.getMaxForwards() <= 0)
        {
            SipUtils.sendResponse(Response.TOO_MANY_HOPS, sipProvider, messageFactory, request, serverTransaction);

            if (log.isDebugEnabled())
                log.debug("Too many hops.");
            return false;
        }

        /**
         * An element MAY check for forwarding loops before forwarding a
         * request.
         */
        if (checkLoopDetection(request, sipProvider))
        {
            SipUtils.sendResponse(Response.LOOP_DETECTED, sipProvider, messageFactory, request, serverTransaction);

            if (log.isDebugEnabled())
                log.debug("Loop detected. "+Response.LOOP_DETECTED+" replied.");
            return false;
        }

        /**
         * 5. Proxy-Require check
         */
        if (!checkProxyRequire(request))
        {
            if (log.isDebugEnabled())
                log.debug("SIP extensions are not supported.");

            // Let's return a 420 Bad Extension
            Response response = messageFactory.createResponse(Response.BAD_EXTENSION, request);

            // We have to add a Unsupported header listing the Option tags
            // that we don't support:
            ProxyRequireHeader prh = (ProxyRequireHeader) request.getHeader(ProxyRequireHeader.NAME);
            if (prh != null)
            {
                UnsupportedHeader unsupportedHeader = headerFactory.createUnsupportedHeader(prh.getOptionTag());
                response.setHeader(unsupportedHeader);
            }
            if (serverTransaction != null)
                serverTransaction.sendResponse(response);
            else
                sipProvider.sendResponse(response);

            return false;
        }


        if ( authenticationEnabled )
        {
            Request fixedRequest = null;

            URI requestURI = request.getRequestURI();
            if (requestURI.isSipURI())
            {
                String host = ((SipURI) requestURI).getHost();
                if (addrMatchesInterface(host))
                {
                    fixedRequest = (Request) request.clone();

                    ((SipURI) fixedRequest.getRequestURI()).setHost(getDefaultDomain());

                    URI toURI = ((ToHeader) fixedRequest.getHeader(ToHeader.NAME)).getAddress().getURI();
                    if (toURI.isSipURI())
                        ((SipURI) toURI).setHost(getDefaultDomain());
                }
            }

            boolean requestAuthorized;

            try
            {
                requestAuthorized = checkProxyAuthorization(fixedRequest == null ? request : fixedRequest, dsam, locationService);
            }
            catch (UserNotFoundException ex)
            {
                if (log.isDebugEnabled())
                    log.debug(ex.getMessage());

                requestAuthorized = false;
            }
            catch (RemoteException ex)
            {
                if (log.isDebugEnabled())
                    log.debug("Connection to Location Service lost.");

                SipUtils.sendResponse(Response.SERVER_INTERNAL_ERROR, sipProvider, messageFactory, request, serverTransaction);
                return false;
            }

            if (!requestAuthorized)
            {
                if (log.isDebugEnabled())
                    log.debug("Request rejected ( Unauthorized )");

                Response response = messageFactory.createResponse(Response.PROXY_AUTHENTICATION_REQUIRED,request);

                ProxyAuthenticateHeader proxyAuthenticateHeader = headerFactory.createProxyAuthenticateHeader("Digest");
                proxyAuthenticateHeader.setParameter("realm",dsam.getDefaultRealm());
                proxyAuthenticateHeader.setParameter("nonce",dsam.generateNonce(dsam.getPreferredAlgorithm()));
                proxyAuthenticateHeader.setParameter("opaque","");
                proxyAuthenticateHeader.setParameter("stale","FALSE");
                proxyAuthenticateHeader.setParameter("algorithm", dsam.getPreferredAlgorithm());

                response.setHeader(proxyAuthenticateHeader);

                if (serverTransaction != null)
                    serverTransaction.sendResponse(response);
                else
                    sipProvider.sendResponse(response);

                return false;
            }
        }

        // Let's add some more important basics checks:
        // - From tag presence.

        /*if (!checkFromTag(request))
            {
                sendResponse(Response.BAD_REQUEST, sipProvider, sipMaster.getMessageFactory(), request, serverTransaction);
                return false;
            } */


        return true;
    }

    /**
     * Perfoms authorization on request
     * @param request Request
     * @param dsam
     * @return true if request has passed authorization
     * @throws openjsip.remote.locationservice.UserNotFoundException If specified subscriber in request was not found in location service database
     * @throws RemoteException Location Service connection troubles
     */
    private boolean checkProxyAuthorization(Request request, DigestServerAuthenticationMethod dsam, LocationServiceInterface locationService) throws UserNotFoundException, RemoteException
    {
        ProxyAuthorizationHeader proxyAuthorizationHeader = (ProxyAuthorizationHeader) request.getHeader(ProxyAuthorizationHeader.NAME);

        if (proxyAuthorizationHeader==null)
        {
            if (log.isDebugEnabled())
                log.debug("Authentication failed: ProxyAuthorization header missing!");

            return false;
        }
        else
        {
            String key = SipUtils.getKeyToLocationService(request);
            String username = locationService.getUsername(key);
            String password = locationService.getPassword(key);
            if (password == null) password = "";

            String username_h = proxyAuthorizationHeader.getParameter("username");
            if (username_h == null) return false;

            if (username_h.indexOf('@') != -1) username_h = username_h.substring(0, username_h.indexOf('@'));

            // If user names are not equal, authorization failed
            if (!username.equals(username_h)) return false;

            return dsam.doAuthenticate(request, proxyAuthorizationHeader, username_h, password);
        }
    }

    /**
     * Returns whether loop detected
     * @param request Request
     * @return true if request is looped, false otherwise
     */
    private boolean checkLoopDetection(Request request, SipProvider sipProvider)
    {
        /**
         *  4. Optional Loop Detection check
         *
         * An element MAY check for forwarding loops before forwarding a
         * request.  If the request contains a Via header field with a sent-
         * by value that equals a value placed into previous requests by the
         * proxy, the request has been forwarded by this element before.  The
         * request has either looped or is legitimately spiraling through the
         * element.  To determine if the request has looped, the element MAY
         * perform the branch parameter calculation described in Step 8 of
         * Section 16.6 on this message and compare it to the parameter
         * received in that Via header field.  If the parameters match, the
         * request has looped.  If they differ, the request is spiraling, and
         * processing continues.  If a loop is detected, the element MAY
         * return a 482 (Loop Detected) response.
         */
        ListIterator viaList = request.getHeaders(ViaHeader.NAME);
        if (viaList != null && viaList.hasNext())
        {
            ViaHeader viaHeader = (ViaHeader) viaList.next();

            ListeningPoint[] lps = sipProvider.getListeningPoints();

            String viaHost = viaHeader.getHost();
            int viaPort = viaHeader.getPort();

            if ( (viaHost.equals(lps[0].getIPAddress()) || viaHost.equalsIgnoreCase(getHostname(sipProvider)) ) && viaPort == lps[0].getPort())
            {
                /**
                 * @todo We have to check the branch-ids...
                 */
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if this proxy supports required extensions
     * @param request Request
     * @return true / false
     */
    private boolean checkProxyRequire(Request request)
    {
        ProxyRequireHeader prh = (ProxyRequireHeader) request.getHeader(ProxyRequireHeader.NAME);
        if (prh == null) return true;
        else
        {
            // We don't support any option tags. So we reject the request:
            return false;
        }
    }

    /**
     * Creates a new ServerTransaction object that will handle the request if necessary and if request type is to be
     * handled by transactions.
     * @param sipProvider SipProvider object
     * @param request Incoming request
     * @param st ServerTransaction that was retrieved from RequestEvent
     * @return
     */
    private ServerTransaction checkServerTransaction(SipProvider sipProvider, Request request, ServerTransaction st)
    {
        ServerTransaction serverTransaction = st;

        // ACKs are not handled by transactions
        if (operationMode == STATEFULL_MODE && serverTransaction == null && !request.getMethod().equals(Request.ACK))
        {
            try
            {
                serverTransaction = sipProvider.getNewServerTransaction(request);

                snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_SERVER_TRANSACTIONS);

                TransactionsMapping transactionsMapping = (TransactionsMapping) serverTransaction.getApplicationData();
                if (transactionsMapping == null)
                {
                    transactionsMapping = new TransactionsMapping(serverTransaction, sipProvider);
                    serverTransaction.setApplicationData(transactionsMapping);
                }
            }
            catch (TransactionAlreadyExistsException ex)
            {
                if (log.isTraceEnabled())
                    log.trace(ex + " is a retransmission.");
            }
            catch (TransactionUnavailableException ex)
            {
                if (log.isTraceEnabled())
                    log.trace("ServerTransaction cannot be created for this request: " + ex);
            }
        }


        return serverTransaction;
    }


    /**
     * Forwards request to specified target.
     * @param targetURI Target to forward request. If it is null, target will be existing RequestURI of request or taken from Route header if exists.
     * @param request Request to forward
     * @param sipProvider SipProvider object
     * @param serverTransaction ServerTransaction that handles this request. If null, the request will be forwarded statelessly.
     * @param statefullForwarding If true and serverTransaction != null, the request will be forwarded statefully, otherwise, it will be forwarded statelessly.
     * @throws InvalidArgumentException
     * @throws ParseException
     * @throws SipException
     */
    public void forwardRequest(URI targetURI, Request request, SipProvider sipProvider, ServerTransaction serverTransaction, boolean statefullForwarding) throws InvalidArgumentException, ParseException, SipException
    {
        // Statefull means the use of transactions
        statefullForwarding = statefullForwarding & (serverTransaction != null);

        /**
         * RFC 3261: 16.6. Request Forwarding For each target, the proxy
         * forwards the request following these steps:
         *
         * 1. Make a copy of the received request
         *
         * 2. Update the Request-URI
         *
         * 3. Update the Max-Forwards header field
         *
         * 4. Optionally add a Record-route header field value
         *
         * 5. Optionally add additional header fields
         *
         * 6. Postprocess routing information
         *
         * 7. Determine the next-hop address, port, and transport
         *
         * 8. Add a Via header field value
         *
         * 9. Add a Content-Length header field if necessary
         *
         * 10. Forward the new request
         *
         * 11. Set timer C
         */

        // Get the parameters and the transport of the request URI
        URI requestURI = request.getRequestURI();

        if (log.isTraceEnabled())
            log.trace("Forwarding request to " + targetURI + (statefullForwarding ? " statefully" : " statelessly"));

        /**
         * 1. Copy request
         *
         * The proxy starts with a copy of the received request.
         */
        Request clonedRequest = (Request) request.clone();

        /**
         * 2. Request-URI
         *
         * The Request-URI in the copy's start line MUST be replaced with
         * the URI for this target.  If the URI contains any parameters
         * not allowed in a Request-URI, they MUST be removed.
         * This is the essence of a proxy's role. This is the mechanism
         * through which a proxy routes a request toward its
         * destination.
         *
         * In some circumstances, the received Request-URI is placed
         * into the target set without being modified. For that target,
         * the replacement above is effectively a no-op.
         *
         * Note -- this should only be done if the target domain is
         * managed by the proxy server.
         */

        if (requestURI.isSipURI())
        {
            if (targetURI != null/* && isDomainServed(((SipURI) requestURI).getHost())*/)
            {
                clonedRequest.setRequestURI(SipUtils.getCanonicalizedURI(targetURI));
                if (log.isTraceEnabled())
                    log.trace("RequestURI replaced with " + clonedRequest.getRequestURI());
            }
        }
        else
        {
            if (log.isDebugEnabled())
                log.debug("Forwarding not SIP requests is currently not implemented.");
            return;
        }

        /**
         *   3. Max-Forwards
         *
         * If the copy contains a Max-Forwards header field, the proxy
         * MUST decrement its value by one (1).
         *
         * If the copy does not contain a Max-Forwards header field, the
         * proxy MUST add one with a field value, which SHOULD be 70.
         *
         * Some existing UAs will not provide a Max-Forwards header field
         * in a request.
         */

        MaxForwardsHeader mf = (MaxForwardsHeader) clonedRequest.getHeader(MaxForwardsHeader.NAME);
        if (mf == null)
        {
            mf = headerFactory.createMaxForwardsHeader(70);
            clonedRequest.addHeader(mf);

            if (log.isTraceEnabled())
                log.trace("Max-Forwards header is missing. Created and added to the cloned request.");
        }
        else
        {
            mf.setMaxForwards(mf.getMaxForwards() - 1);

            if (log.isTraceEnabled())
                log.trace("Max-Forwards value decremented by one. It is now: " + mf.getMaxForwards());
        }

        /**
         * 4. Record-Route
         *
         * If this proxy wishes to remain on the path of future requests
         * in a dialog created by this request (assuming the request
         * creates a dialog), it MUST insert a Record-Route header field
         * value into the copy before any existing Record-Route header
         * field values, even if a Route header field is already present.
         * ...
         * The URI placed in the Record-Route header field value MUST be
         * a SIP or SIPS URI. This URI MUST contain an lr parameter (see
         * Section 19.1.1). This URI MAY be different for each
         * destination the request is forwarded to. The URI SHOULD NOT
         * contain the transport parameter.
         */

        /**
         * @todo Read all about transport !
         */

        ListeningPoint[] lps = sipProvider.getListeningPoints();

        SipURI sipURI = addressFactory.createSipURI(null, getHostname(sipProvider));
        sipURI.setPort(lps[0].getPort());

        Address address = addressFactory.createAddress(null, sipURI);
        RecordRouteHeader recordRouteHeader = headerFactory.createRecordRouteHeader(address);

        // lr parameter to add. This proxy is a Loose Router.
        recordRouteHeader.setParameter("lr", null);
        clonedRequest.addFirst(recordRouteHeader);

        if (log.isTraceEnabled())
            log.trace("Added Record-Route header: " + recordRouteHeader);

        /**
         * 5. Add Additional Header Fields
         * The proxy MAY add any other appropriate header fields to the
         * copy at this point.
         */

        //if (log.isTraceEnabled())
        //    log.trace("No additional headers to add.");

        /**
         *  6. Postprocess routing information
         *
         * A proxy MAY have a local policy that mandates that a request
         * visit a specific set of proxies before being delivered to the
         * destination.  A proxy MUST ensure that all such proxies are
         * loose routers.  Generally, this can only be known with
         * certainty if the proxies are within the same administrative
         * domain.  This set of proxies is represented by a set of URIs
         * (each of which contains the lr parameter).  This set MUST be
         * pushed into the Route header field of the copy ahead of any
         * existing values, if present.  If the Route header field is
         * absent, it MUST be added, containing that list of URIs.
         * ...
         * If the copy contains a Route header field, the proxy MUST
         * inspect the URI in its first value.  If that URI does not
         * contain an lr parameter, the proxy MUST modify the copy as
         * follows:
         *  -  The proxy MUST place the Request-URI into the Route header
         *     field as the last value.
         *
         *  -  The proxy MUST then place the first Route header field value
         *     into the Request-URI and remove that value from the Route
         *     header field.
         */

        if (log.isTraceEnabled())
            log.trace("Postprocessing routing information...");

        ListIterator routes = clonedRequest.getHeaders(RouteHeader.NAME);
        if (routes != null && routes.hasNext())
        {
            RouteHeader routeHeader = (RouteHeader) routes.next();
            Address routeAddress = routeHeader.getAddress();
            URI routeURI = routeAddress.getURI();

            if (routeURI.isSipURI() && (!((SipURI) routeURI).hasLrParam()))
            {
                RouteHeader routeHeaderToAdd = headerFactory.createRouteHeader(addressFactory.createAddress(clonedRequest.getRequestURI()));
                clonedRequest.addLast(routeHeaderToAdd);

                clonedRequest.setRequestURI(routeURI);
                clonedRequest.removeFirst(RouteHeader.NAME);

                if (log.isTraceEnabled())
                    log.trace("RequestURI placed to the end of Route headers, and first Route header " + routeURI + " was set as RequestURI");

                /**
                 * Appending the Request-URI to the Route header field is part of
                 * a mechanism used to pass the information in that Request-URI
                 * through strict-routing elements.  "Popping" the first Route
                 * header field value into the Request-URI formats the message the
                 * way a strict-routing element expects to receive it (with its
                 * own URI in the Request-URI and the next location to visit in
                 * the first Route header field value).
                 */
            }
            else if (log.isTraceEnabled())
                log.trace("First Route header " + routeHeader + " is not SIP URI or it doesn't contain lr parameter");
        }
        else
        {
            if (log.isTraceEnabled())
                log.trace("No postprocess routing information to do (No routes detected).");
        }

        if (log.isTraceEnabled())
            log.trace("Postprocessing finished.");

        /**
         * 7. Determine Next-Hop Address, Port, and Transport
         * The proxy MAY have a local policy to send the request to a
         * specific IP address, port, and transport, independent of the
         * values of the Route and Request-URI.  Such a policy MUST NOT be
         * used if the proxy is not certain that the IP address, port, and
         * transport correspond to a server that is a loose router.
         * However, this mechanism for sending the request through a
         * specific next hop is NOT RECOMMENDED; instead a Route header
         *
         * field should be used for that purpose as described above.
         * In the absence of such an overriding mechanism, the proxy
         * applies the procedures listed in [4] as follows to determine
         * where to send the request.
         */

        // Next-Hop Address, Port, and Transport is processed by the stack.

        /**
         * @todo add manual nexthop detection to find outgoing SipProvider
         */

        /**
         * 8. Add a Via header field value
         *
         * The proxy MUST insert a Via header field value into the copy
         * before the existing Via header field values.  The construction
         * of this value follows the same guidelines of Section 8.1.1.7.
         * This implies that the proxy will compute its own branch
         * parameter, which will be globally unique for that branch, and
         * contain the requisite magic cookie. Note that this implies that
         * the branch parameter will be different for different instances
         * of a spiraled or looped request through a proxy.
         */

        String branchId = SipUtils.generateBranchId();

        if (operationMode == STATELESS_MODE)
        {
            /**
             * A stateless proxy MUST follow the request processing steps described
             * in Section 16.6 with the following exceptions:
             *
             *    The requirement for unique branch IDs across space and time
             *    applies to stateless proxies as well.  However, a stateless
             *    proxy cannot simply use a random number generator to compute
             *    the first component of the branch ID, as described in Section
             *    16.6 bullet 8.  This is because retransmissions of a request
             *    need to have the same value, and a stateless proxy cannot tell
             *    a retransmission from the original request.  Therefore, the
             *    component of the branch parameter that makes it unique MUST be
             *    the same each time a retransmitted request is forwarded.  Thus
             *    for a stateless proxy, the branch parameter MUST be computed as
             *    a combinatoric function of message parameters which are
             *    invariant on retransmission.
             *
             *    The stateless proxy MAY use any technique it likes to guarantee
             *    uniqueness of its branch IDs across transactions.  However, the
             *    following procedure is RECOMMENDED.  The proxy examines the
             *    branch ID in the topmost Via header field of the received
             *    request.  If it begins with the magic cookie, the first
             *    component of the branch ID of the outgoing request is computed
             *    as a hash of the received branch ID.  Otherwise, the first
             *    component of the branch ID is computed as a hash of the topmost
             *    Via, the tag in the To header field, the tag in the From header
             *    field, the Call-ID header field, the CSeq number (but not
             *    method), and the Request-URI from the received request.  One of
             *    these fields will always vary across two different
             *    transactions.
             */

            try
            {
                ViaHeader topmostViaHeader = (ViaHeader) request.getHeader(ViaHeader.NAME);
                if (topmostViaHeader != null)
                {
                    MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                    String branch = topmostViaHeader.getBranch();

                    if (branch.startsWith(SipUtils.BRANCH_MAGIC_COOKIE))
                    {
                        byte[] bytes = messageDigest.digest(Integer.toString(branch.hashCode()).getBytes());
                        branchId = SipUtils.toHexString(bytes);
                    }
                    else
                    {
                        String via = topmostViaHeader.toString().trim();
                        String toTag = ((ToHeader) request.getHeader(ToHeader.NAME)).getTag();
                        String fromTag = ((FromHeader) request.getHeader(FromHeader.NAME)).getTag();
                        String callid = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
                        long cseq = ((CSeqHeader) request.getHeader(CSeqHeader.NAME)).getSeqNumber();
                        String requestUri = requestURI.toString().trim();

                        byte[] bytes = messageDigest.digest( (via + toTag + fromTag + callid + cseq + requestUri).getBytes() );
                        branchId = SipUtils.toHexString(bytes);
                    }
                }

            }
            catch(NoSuchAlgorithmException ex)
            {

            }
        }

        /**
         * @todo fix transport
         * @todo place hostname of outgoing interface determined in step 7.
         */
        ViaHeader viaHeader = headerFactory.createViaHeader(getHostname(sipProvider), lps[0].getPort(), lps[0].getTransport(), branchId);
        clonedRequest.addFirst(viaHeader);

        if (log.isTraceEnabled())
            log.trace("Added Via header " + viaHeader);

        /**
         * Proxies choosing to detect loops have an additional
         * constraint in the value they use for construction of the
         * branch parameter. A proxy choosing to detect loops SHOULD
         * create a branch parameter separable into two parts by the
         * implementation. The first part MUST satisfy the constraints
         * of Section 8.1.1.7 as described above. The second is used to
         * perform loop detection and distinguish loops from spirals.
         */

        /**
         * @todo Implement advanced branch parameter for loop detection
         */

        /**
         *  9. Add a Content-Length header field if necessary
         *
         * If the request will be sent to the next hop using a stream-
         * based transport and the copy contains no Content-Length header
         * field, the proxy MUST insert one with the correct value for the
         * body of the request (see Section 20.14).
         */

        ContentLengthHeader contentLengthHeader = (ContentLengthHeader) clonedRequest.getHeader(ContentLengthHeader.NAME);
        if (contentLengthHeader == null)
        {
            byte[] contentData = request.getRawContent();
            contentLengthHeader = headerFactory.createContentLengthHeader(contentData == null ? 0 : contentData.length);
            clonedRequest.setContentLength(contentLengthHeader);

            if (log.isTraceEnabled())
                log.trace("Added Content-Length header " + contentLengthHeader);
        }
        else if (log.isTraceEnabled())
            log.trace("Leaving existing Content-Length header untouched.");

        /**
         *  10. Forward Request
         *
         * A stateful proxy MUST create a new client transaction for this
         * request as described in Section 17.1 and instructs the
         * transaction to send the request using the address, port and
         * transport determined in step 7.
         */
        if (log.isTraceEnabled())
        {
            log.trace("Forwarding request " + (statefullForwarding ? "statefully" : "statelessly"));
            log.trace("Outgoing request:\n" + clonedRequest);
        }

        if (!statefullForwarding)
        {
            sipProvider.sendRequest(clonedRequest);

            if (log.isDebugEnabled())
                log.debug("Request forwarded statelessly.");
        }
        else
        {
            /**
             * A stateful proxy MUST create a new client transaction for this
             * request as described in Section 17.1 and instructs the
             * transaction to send the request using the address, port and
             * transport determined in step 7
             */
            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(clonedRequest);

            if (log.isTraceEnabled())
                log.trace("Client transaction for request is: "+clientTransaction);

            snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_CLIENT_TRANSACTIONS);

            // Get TransactionMapping object from server transaction
            TransactionsMapping transactionMapping = (TransactionsMapping) serverTransaction.getApplicationData();
            if (transactionMapping == null)
            {
                transactionMapping = new TransactionsMapping(serverTransaction, sipProvider);
                serverTransaction.setApplicationData(transactionMapping);
            }

            // Add client transaction
            transactionMapping.addClientTransaction(clientTransaction);

            // Remember to which TransactionMapping object client transaction is related
            clientTransaction.setApplicationData(transactionMapping);

            /**
             * 11. Set timer C
             *
             * In order to handle the case where an INVITE request never
             * generates a final response, the TU uses a timer which is called
             * timer C.  Timer C MUST be set for each client transaction when
             * an INVITE request is proxied.  The timer MUST be larger than 3
             * minutes.  Section 16.7 bullet 2 discusses how this timer is
             * updated with provisional responses, and Section 16.8 discusses
             * processing when it fires.
             */

            if (clonedRequest.getMethod().equals(Request.INVITE))
            {
                Timer timer = new Timer();
                TimerCTask timerTask = new TimerCTask(clientTransaction, sipProvider, this, log);

                transactionMapping.registerTimerC(timer, clientTransaction);

                if (log.isTraceEnabled())
                    log.trace("Timer C created for proxied CT "+clientTransaction);

                timer.schedule(timerTask, timercPeriod);
            }

            // Send request statefully
            clientTransaction.sendRequest();

            if (log.isDebugEnabled())
                log.debug("Request forwarded statefully.");
        }
    }

    /**
     * *****************************************************************************************
     * *****************************************************************************************
     *                           R E S P O N S E    P R O C E S S I N G
     * *****************************************************************************************
     * *****************************************************************************************
     */

    /**
     * Processes incoming responses and forwards them if necessary.
     * @param responseEvent ResponseEvent object
     * @throws InvalidArgumentException
     * @throws ParseException
     * @throws SipException
     */
    private void processIncomingResponse(ResponseEvent responseEvent) throws InvalidArgumentException, ParseException, SipException
    {
        Response response = responseEvent.getResponse();
        SipProvider sipProvider = (SipProvider) responseEvent.getSource();
        ClientTransaction clientTransaction = (operationMode == STATEFULL_MODE ? responseEvent.getClientTransaction() : null);
        int statusCode = response.getStatusCode();
        String reason = response.getReasonPhrase();
        CSeqHeader cseqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        ContactHeader contactHeader = (ContactHeader) response.getHeader(ContactHeader.NAME);

        if (log.isDebugEnabled())
        {
            log.debug("-------------------");
            log.debug("Incoming " + cseqHeader.getMethod() + " response from "+(contactHeader != null ? contactHeader.getAddress() : "No Contact header"));
            log.debug("Status: " + statusCode + " reason: " + reason);
        }

        if (log.isTraceEnabled())
            log.trace("\n" + response.toString());
      

        /**
         * 16.7 Response Processing
         *
         * When a response is received by an element, it first tries to locate a
         * client transaction (Section 17.1.3) matching the response.  If none
         * is found, the element MUST process the response (even if it is an
         * informational response) as a stateless proxy (described below).  If a
         * match is found, the response is handed to the client transaction.
         *
         *      Forwarding responses for which a client transaction (or more
         *      generally any knowledge of having sent an associated request) is
         *      not found improves robustness.  In particular, it ensures that
         *     "late" 2xx responses to INVITE requests are forwarded properly.
         */

        if (clientTransaction == null)
        {
            if (log.isTraceEnabled())
                log.trace("ClientTransaction is null. Forwarding the response statelessly.");

            processResponseStatelessly(response, sipProvider);
            return;
        }

        /**
         * The proxy locates the "response context" it created before
         * forwarding the original request using the key described in
         * Section 16.6. The remaining processing steps take place in this
         * context.
         */
        // The response context feature in bundled with TransactionMapping object
        TransactionsMapping transactionsMapping = (TransactionsMapping) clientTransaction.getApplicationData();
        if (transactionsMapping == null)
        {
            if (log.isDebugEnabled())
                log.debug("Response context cannot be found for this response. Forwarding the response statelessly.");

            processResponseStatelessly(response, sipProvider);
            return;
        }

        // Server transaction object cannot be null because TransactionMapping constructor throws NullPointerException,
        // but we check it anyway for sure
        ServerTransaction serverTransaction = transactionsMapping.getServerTransaction();
        if (serverTransaction == null)
        {
            if (log.isDebugEnabled())
                log.debug("Server transaction cannot be found for this response. Dropped.");

            return;
        }

        /**
         * 2. Update timer C for provisional responses
         *
         * For an INVITE transaction, if the response is a provisional
         * response with status codes 101 to 199 inclusive (i.e., anything
         * but 100), the proxy MUST reset timer C for that client
         * transaction.  The timer MAY be reset to a different value, but
         * this value MUST be greater than 3 minutes.
         */

        if (statusCode >= 101 && statusCode <= 199 && cseqHeader.getMethod().equals(Request.INVITE))
        {
            // As java.util.Timer cannot update delays,  we have to recreate one
            transactionsMapping.cancelTimerC(clientTransaction);

            Timer timer = new Timer();
            TimerCTask timerTask = new TimerCTask(clientTransaction, sipProvider, this, log);

            transactionsMapping.registerTimerC(timer, clientTransaction);
            timer.schedule(timerTask, timercPeriod);

            if (log.isTraceEnabled())
                log.trace("Timer C updated for CT "+clientTransaction);
        }

        /**
         * 3.  Via
         *
         * The proxy removes the topmost Via header field value from the
         * response. If no Via header field values remain in the response,
         * the response was meant for this element and MUST NOT be
         * forwarded. The remainder of the processing described in this
         * section is not performed on this message, the UAC processing
         * rules described in Section 8.1.3 are followed instead (transport
         * layer processing has already occurred).
         */

        if (log.isTraceEnabled())
            log.trace("Removing topmost Via header.");

        response.removeFirst(ViaHeader.NAME);

        ListIterator viaList = response.getHeaders(ViaHeader.NAME);
        if (viaList == null || !viaList.hasNext())
        {
            if (log.isDebugEnabled())
                log.debug("Response has no more Via headers. The response is for the proxy. Not forwarded.");

            checkResponseContext(transactionsMapping);
            return;
        }

        /**
         * Final responses received are stored in the response context
         * <b>until a final response is generated on the server transaction</b>
         */
        if (serverTransaction.getState().getValue() >= TransactionState._COMPLETED)
        {
            /**
             *  After a final response has been sent on the server transaction,
             *  the following responses MUST be forwarded immediately:
             *
             *  -  Any 2xx response to an INVITE request
             */

            if (statusCode >= 200 && statusCode <= 299 && cseqHeader.getMethod().equals(Request.INVITE))
            {
                sendResponseImmediately(response, transactionsMapping);
                return;
            }
            else
            {
                /**
                 * A stateful proxy MUST NOT immediately forward any other responses.
                 */
                return;
            }
        }

        /**
         * Until a final response has been sent on the server transaction,
         * the following responses MUST be forwarded immediately:
         *
         * -  Any provisional response other than 100 (Trying)
         */
        if (!((SIPResponse) response).isFinalResponse())
        {
            if (statusCode == Response.TRYING)
            {
                if (log.isDebugEnabled())
                    log.debug("Response "+statusCode+" ("+response.getReasonPhrase()+") is not forwarded.");

                return;
            }
            else
            {
                if (log.isTraceEnabled())
                    log.trace("Response is 1XX, so forwarding immediately.");

                sendResponseImmediately(response, transactionsMapping);
                return;
            }
        }

        /**
         * 4.  Add response to context
         *
         * Final responses received are stored in the response context
         * until a final response is generated on the server transaction
         * associated with this context.  The response may be a candidate
         * for the best final response to be returned on that server
         * transaction.  Information from this response may be needed in
         * forming the best response, even if this response is not chosen.
         */
        transactionsMapping.getResponseContext().addFinalResponse(response);

        /**
         * todo Add recursion support if desired
         */

        /**
         * 5.  Check response for forwarding
         *
         * Until a final response has been sent on the server transaction,
         * the following responses MUST be forwarded immediately:
         *
         *      -  Any provisional response other than 100 (Trying)
         *
         *      -  Any 2xx response
         */
        if (statusCode >= 200 && statusCode <= 299)
        {
            if (log.isTraceEnabled())
                log.trace("2XX are to be forwarded immediately.");

            sendResponseImmediately(response, transactionsMapping);
            return;
        }

        /**
         * If a 6xx response is received, it is not immediately forwarded,
         * but the stateful proxy SHOULD cancel all client pending
         * transactions as described in Section 10, and it MUST NOT create
         * any new branches in this context.
         */
        else if (statusCode >= 600)
        {
            cancelPendingTransactions(transactionsMapping.getClientTransactionsArray(), sipProvider);
            /**
             * todo 600, If recursion support is added, do not create any new branches in this context.
             */
        }

        checkResponseContext(transactionsMapping);
    }


    /**
     * Processes response statelessly
     * @param response Response to forward
     * @param sipProvider SipProvider object
     * @throws SipException
     */
    private void processResponseStatelessly(Response response, SipProvider sipProvider) throws SipException
    {
        /**
         * Response processing as described in Section 16.7 does not apply to a
         * proxy behaving statelessly.  When a response arrives at a stateless
         * proxy, the proxy MUST inspect the sent-by value in the first
         * (topmost) Via header field value.  If that address matches the proxy,
         * (it equals a value this proxy has inserted into previous requests)
         * the proxy MUST remove that header field value from the response and
         * forward the result to the location indicated in the next Via header
         * field value.  The proxy MUST NOT add to, modify, or remove the
         * message body.  Unless specified otherwise, the proxy MUST NOT remove
         * any other header field values.  If the address does not match the
         * proxy, the message MUST be silently discarded.
         */
        ListIterator viaList = response.getHeaders(ViaHeader.NAME);
        if (viaList != null && viaList.hasNext())
        {
            ViaHeader viaHeader = (ViaHeader) viaList.next();
            String viaHost = viaHeader.getHost();
            int viaPort = viaHeader.getPort();
            if (viaPort == -1) viaPort = 5060;

            ListeningPoint[] lps = sipProvider.getListeningPoints();

            if ( addrMatchesInterface(viaHost) && viaPort == lps[0].getPort() )
            {
                if (log.isTraceEnabled())
                    log.trace("Top Via header matches proxy. Removing first Via header.");

                response.removeFirst(ViaHeader.NAME);

                viaList = response.getHeaders(ViaHeader.NAME);
                if (viaList.hasNext())
                {
                    sipProvider.sendResponse(response);

                    if (log.isDebugEnabled())
                        log.debug("Response forwarded statelessly.");

                    if (log.isTraceEnabled())
                        log.trace("\n"+response);
                }
            }
        }
        else if (log.isDebugEnabled())
            log.debug("Via address doesn't match proxy or no Via headers left. Response is dropped.");
    }

    /**
     * Generates and sends CANCEL requests for all pending client transactions.
     * @param clientTransactions An array with client transactions. Each will be checked for being "pending".
     * @param sipProvider SipProvider object
     * @throws SipException
     */
    private void cancelPendingTransactions(ClientTransaction[] clientTransactions, SipProvider sipProvider) throws SipException
    {
        for (int i = 0; i < clientTransactions.length; i++)
        {
            ClientTransaction clientTransaction = clientTransactions[i];

            /**
             * A pending client transaction is one that has
             * received a provisional response, but no final response (it is
             * in the proceeding state) and has not had an associated CANCEL
             * generated for it.
             */
            if (log.isTraceEnabled())
                log.trace("Found "+clientTransaction.getState());

            if (clientTransaction.getState().equals(TransactionState.PROCEEDING))
            {
                /**
                 * 9.1: The following procedures are used to
                 * construct a CANCEL request. The Request-URI,
                 * Call-ID, To, the numeric part of CSeq, and From
                 * header fields in the CANCEL request MUST be
                 * identical to those in the request being
                 * cancelled, including tags. A CANCEL constructed
                 * by a client MUST have only a single Via header
                 * field value matching the top Via value in the
                 * request being cancelled. Using the same values
                 * for these header fields allows the CANCEL to be
                 * matched with the request it cancels (Section 9.2
                 * indicates how such matching occurs). However, the
                 * method part of the CSeq header field MUST have a
                 * value of CANCEL. This allows it to be identified
                 * and processed as a transaction in its own right
                 * (See Section 17).
                 *
                 * If the request being cancelled contains a Route
                 * header field, the CANCEL request MUST include
                 * that Route header field's values.
                 */

                // All this stuff is implemented in createCancel() method.
                Request cancelRequest = clientTransaction.createCancel();
                ClientTransaction cancelTransaction = sipProvider.getNewClientTransaction(cancelRequest);

                snmpAssistant.incrementSnmpInteger(SNMP_OID_NUM_CLIENT_TRANSACTIONS);

                cancelTransaction.sendRequest();

                if (log.isTraceEnabled())
                    log.trace("Cancel request for transaction " + clientTransaction + " is sent.");
            }
        }
    }

    /**
     * This functions gets the best response from response context and forwards it to recipient under following circumstances:
     *   - Server transaction is not yet completed
     *   - All client transactions are completed
     * @param transactionsMapping Transactions mapping object
     */
    private void checkResponseContext(TransactionsMapping transactionsMapping)
    {
        try
        {
            ServerTransaction serverTransaction = transactionsMapping.getServerTransaction();
            if (serverTransaction.getState().getValue() >= TransactionState._COMPLETED) return;

            ClientTransaction[] clientTransactions = transactionsMapping.getClientTransactionsArray();
            for (int i=0; i<clientTransactions.length; i++)
                if (clientTransactions[i].getState().getValue() < TransactionState._COMPLETED) return;

            Response bestResponse = transactionsMapping.getResponseContext().getBestResponse(messageFactory);
            if (bestResponse == null)
            {
                if (log.isDebugEnabled())
                    log.debug("Cannot determine best response (null). Code debug required.");
                return;
            }

            sendResponseImmediately(bestResponse, transactionsMapping);
        }
        catch (Exception ex)
        {
            if (log.isDebugEnabled())
                log.debug("Exception raised int checkResponseContext() method: " + ex.getMessage());
            if (log.isTraceEnabled())
                log.trace("", ex);                        
        }
    }

    /**
     * Processes steps of response immediate forwarding.
     * See section 16.7 step 5.
     *    Any response chosen for immediate forwarding MUST be processed
     *    as described in steps "Aggregate Authorization Header Field
     *    Values" through "Record-Route".
     * @param outgoingResponse Response to send
     * @param transactionsMapping Transaction mapping
     * @throws InvalidArgumentException
     * @throws SipException
     */
    private void sendResponseImmediately(Response outgoingResponse, TransactionsMapping transactionsMapping) throws InvalidArgumentException, SipException
    {
        ServerTransaction serverTransaction = transactionsMapping.getServerTransaction();

        /**
         *  8.  Record-Route
         *
         * If the selected response contains a Record-Route header field
         * value originally provided by this proxy, the proxy MAY choose
         * to rewrite the value before forwarding the response.  This
         * allows the proxy to provide different URIs for itself to the
         * next upstream and downstream elements.  A proxy may choose to
         * use this mechanism for any reason.  For instance, it is useful
         * for multi-homed hosts.
         */

        /**
         * @todo Do we need to rewrite Record-Route header when receiving responses ?
         */

        /**
         * 9.  Forward response
         * ...
         * The proxy MUST NOT add to, modify, or
         * remove the message body.  Unless otherwise specified, the proxy
         * MUST NOT remove any header field values other than the Via
         * header field value discussed in Section 16.7 Item 3.  In
         * particular, the proxy MUST NOT remove any "received" parameter
         * it may have added to the next Via header field value while
         * processing the request associated with this response.  The
         * proxy MUST pass the response to the server transaction
         * associated with the response context.  This will result in the
         * response being sent to the location now indicated in the
         * topmost Via header field value.  If the server transaction is
         * no longer available to handle the transmission, the element
         * MUST forward the response statelessly by sending it to the
         * server transport.  The server transaction might indicate
         * failure to send the response or signal a timeout in its state
         * machine.  These errors would be logged for diagnostic purposes
         * aXths appropriate, but the protocol requires no remedial action
         * from the proxy.
         *
         * The proxy MUST maintain the response context until all of its
         * associated transactions have been terminated, even after
         * forwarding a final response.
         */
        if (serverTransaction.getState().getValue() < TransactionState._COMPLETED)
        {
            serverTransaction.sendResponse(outgoingResponse);

            if (log.isDebugEnabled())
                log.debug("Response is statefully forwarded.");
        }
        else
        {
            if (log.isDebugEnabled())
                log.debug("Sending response statelessly because associated server transaction's state is already " + serverTransaction.getState());

            transactionsMapping.getSipProvider().sendResponse(outgoingResponse);

            if (log.isDebugEnabled())
                log.debug("Response is statelessly forwarded.");
        }

        if (log.isTraceEnabled())
            log.trace("\n"+outgoingResponse);

        /**
         * 10. Generate CANCELs
         *
         * If the forwarded response was a final response, the proxy MUST
         * generate a CANCEL request for all pending client transactions
         * associated with this response context. A proxy SHOULD also
         * generate a CANCEL request for all pending client transactions
         * associated with this response context when it receives a 6xx
         * response. A pending client transaction is one that has received a
         * provisional response, but no final response (it is in the
         * proceeding state) and has not had an associated CANCEL generated
         * for it. Generating CANCEL requests is described in Section 9.1.
         */        
        if (((SIPResponse) outgoingResponse).isFinalResponse())
        {
            if (log.isTraceEnabled())
                log.trace("Forwarded response is final. Canceling pending transactions.");
            
            cancelPendingTransactions(transactionsMapping.getClientTransactionsArray(), transactionsMapping.getSipProvider());
        }
    }


    public String execCmd(String cmd, String[] parameters) throws RemoteException
    {
        if (cmd == null)
            return null;

        // cmd get
        if (cmd.equalsIgnoreCase("get") && parameters != null)
        {
            if (parameters.length > 0)
            {
                if (parameters[0].equalsIgnoreCase("numRequestsProcessed"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_REQUESTS_PROCESSED).toString();
                else if (parameters[0].equalsIgnoreCase("numResponsesProcessed"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_RESPONSES_PROCESSED).toString();
                else if (parameters[0].equalsIgnoreCase("numRequestsNotProcessed"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_REQUEST_PROCESSING_ERRORS).toString();
                else if (parameters[0].equalsIgnoreCase("numResponsesNotProcessed"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_RESPONSE_PROCESSING_ERRORS).toString();
                else if (parameters[0].equalsIgnoreCase("numServerTransactions"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_SERVER_TRANSACTIONS).toString();
                else if (parameters[0].equalsIgnoreCase("numClientTransactions"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_CLIENT_TRANSACTIONS).toString();
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
        "get numRequestsProcessed       - Get the total number of successfully processed requests.\n" +
        "get numResponsesProcessed      - Get the total number of processed responses.\n" +
        "get numRequestsNotProcessed    - Get the total number of requests not being processed due to internal errors.\n" +
        "get numResponsesNotProcessed   - Get the total number of responses not being processed due to internal errors.\n" +
        "get numServerTransactions      - Get the total number of server transactions that proxy currently maintains.\n" +
        "get numClientTransactions      - Get the total number of client transactions that proxy currently maintains.\n"+
        "get vm_freememory              - Get the amount of free memory in the Java Virtual Machine.\n"+
        "get vm_maxmemory               - Get the maximum amount of memory that the Java virtual machine will attempt to use.\n"+
        "get vm_totalmemory             - Get the total amount of memory in the Java virtual machine.\n";
    }
                              
    public boolean isAlive() throws RemoteException
    {
        return true;
    }

   
}
