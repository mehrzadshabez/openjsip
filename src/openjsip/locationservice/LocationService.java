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
package openjsip.locationservice;

import openjsip.SipUtils;
import openjsip.snmp.SNMPAssistant;
import openjsip.remote.locationservice.LocationServiceInterface;
import openjsip.remote.locationservice.UserNotFoundException;
import openjsip.remote.locationservice.Binding;

import javax.sip.header.ContactHeader;
import javax.sip.address.URI;
import javax.sip.address.AddressFactory;
import javax.sip.SipFactory;
import javax.sip.PeerUnavailableException;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketException;

import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.Logger;
import gov.nist.javax.sip.address.SipUri;
import snmp.*;

/**
 * Location Service: A location service is used by a SIP redirect or
 * proxy server to obtain information about a callee's possible
 * location(s).  It contains a list of bindings of address-of-record 
 * keys to zero or more contact addresses.
 */

public class LocationService extends UnicastRemoteObject implements LocationServiceInterface, Runnable
{
    /**
     * Logger
     */
    private static Logger log = Logger.getLogger(LocationService.class);

    /**
     * Users database
     */
    private final Hashtable<String, UserProfile> database = new Hashtable<String, UserProfile>();

    /**
     * Default domain
     */
    private String defaultDomain;

    /**
     * Set of responsible domains
     */
    private final HashSet<String> domains = new HashSet<String>();

    /**
     * Timer that check binding for expiration
     */
    private final Timer checkBindingsTimer;

    /**
     * The previous time when check bindings task was run
     */
    private long prevCheckTime;

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
    protected static final String SNMP_ROOT_OID = "1.3.6.1.4.1.1937.1.";
    protected static final String SNMP_OID_NUM_SUBSCRIBERS = SNMP_ROOT_OID + "1.1";
    protected static final String SNMP_OID_NUM_BINDINGS = SNMP_ROOT_OID + "1.2";

    /**
     * SNMP database with default values.
     */
    private static final Object SNMP_DATABASE[][] = new Object[][]
    {
        { SNMP_OID_NUM_SUBSCRIBERS, new SNMPGauge32(0) },
        { SNMP_OID_NUM_BINDINGS, new SNMPGauge32(0) },
    };



    /**
     * Entry point
     * @param args Command line arguments
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
                System.err.println("Error: Cannot open configuration file "+args[0]);
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

        String rmiName = props.getProperty("location.service.rmi.objectname", "LocationService").trim();
        String rmiHost = props.getProperty("location.service.rmi.host", "localhost").trim();
        int rmiPort = 1099;

        try
        {
            rmiPort = Integer.parseInt(props.getProperty("location.service.rmi.port", "1099").trim());
        }
        catch(NumberFormatException ex)
        {
            // ignored
        }

        // Try to register Location Service within RMI registry
        try
        {
            if (System.getSecurityManager() == null)
            {
                System.setSecurityManager(new SecurityManager());
            }

            RMIBindName = "rmi://" + rmiHost + ":" + rmiPort + "/" + rmiName;

            LocationServiceInterface locationService = new LocationService(props);
            Naming.rebind(RMIBindName, locationService);

            if (log.isInfoEnabled())
                log.info("Location Service registered as \"" + rmiName + "\" within RMI registry at " + rmiHost + ":" + rmiPort);

            if (log.isInfoEnabled())
                log.info("Location Service started...");
        }
        catch(ConnectException ex)
        {
            log.error("Cannot register within RMI registry at "+ rmiHost +":"+ rmiPort, ex);
            System.exit(1);
        }
        catch(Exception ex)
        {
            log.error("", ex);
            System.exit(1);
        }

    }

    /**
     * Prints program usage help
     */
    private static void printUsage()
    {
        System.out.println("\nUsage: LocationService <location.properties file>\n" +
                           "   where location.properties is the path to .properties file with settings for Location Service server.");
    }

    /**
     * Location service constructor
     * @param props Location service configuration properties
     * @throws IOException I/O troubles
     * @throws PeerUnavailableException
     */
    public LocationService(Properties props) throws IOException, PeerUnavailableException
    {
        if (log.isInfoEnabled())
            log.info("Starting Location Service server v" + SipUtils.OPENJSIP_VERSION + "...");

        AddressFactory addressFactory = SipFactory.getInstance().createAddressFactory();
                
        // Set default domain
        defaultDomain = props.getProperty("location.service.default.domain", "openjsip.net").trim().toLowerCase();

        if (log.isInfoEnabled())
            log.info("Default domain: "+defaultDomain);

        if (log.isInfoEnabled())
            log.info("Reading user database...");

        String dbPath = props.getProperty("location.service.db.file");
        Properties db = new Properties();

        if (dbPath != null)
        {
            db.load(new FileInputStream(dbPath));
        }

        int maxUsers = 100;

        try
        {
            maxUsers = Integer.parseInt(db.getProperty("user.max", "100").trim());
        }
        catch(NumberFormatException ex)
        {
            // ignored
        }

        maxUsers = Math.max(100, maxUsers);

        /**
         * NOTE: Address-of-Record: An address-of-record (AOR) is a SIP or SIPS URI
         * that points to a domain with a location service that can map
         * the URI to another URI where the user might be available.
         * Typically, the location service is populated through
         * registrations.  An AOR is frequently thought of as the "public
         * address" of the user.
         */
        int numSkipped = 0;


        for (int index = 1; index <= maxUsers; index++)
        {
            String key = db.getProperty("user." + index);
            if (key == null) continue;

            key = key.trim();

            int domainPos = key.indexOf('@');
            if (domainPos == -1) key = key.concat("@" + defaultDomain);

            try
            {
                URI uri = addressFactory.createURI(key);

                if (!uri.isSipURI())
                {
                    numSkipped++;
                    continue;
                }

                SipUri addressOfRecord = (SipUri) uri;
                addressOfRecord.setHost(addressOfRecord.getHost().toLowerCase());

                // Add domain to domain list
                domains.add(addressOfRecord.getHost());

                /**
                 * The URI
                 * MUST then be converted to a canonical form.  To do that, all
                 * URI parameters MUST be removed (including the user-param), and
                 * any escaped characters MUST be converted to their unescaped
                 * form.  The result serves as an index into the list of bindings.
                 */
                key = SipUtils.getKeyToLocationService(addressOfRecord);

                database.put(key, new UserProfile(addressOfRecord));
            }
            catch (Exception ex)
            {
                numSkipped++;
            }

        }

        /**
         * Print records count
         */
        if (log.isInfoEnabled())
        {

            for (String domain : domains)
            {
                int count = 0;

                Enumeration<String> records = database.keys();

                while (records.hasMoreElements())
                {
                    String userAtHost = records.nextElement();
                    if (userAtHost.substring(userAtHost.indexOf('@') + 1).equals(domain))
                    {
                        count++;
                    }
                }

                log.info("Domain " + domain + " contains " + count + " records.");
            }

            if (numSkipped > 0)
                log.info("Skipped " + numSkipped + " records.");
        }


        /**
         * Read SNMP configuration
         */
        boolean isSnmpEnabled = props.getProperty("location.service.snmp.agent.enabled", "yes").trim().equalsIgnoreCase("yes");
        if (isSnmpEnabled)
        {
            int snmpPort = 1161;

            try
            {
                snmpPort = Integer.parseInt(props.getProperty("location.service.snmp.agent.port", "1161").trim());
            }
            catch (NumberFormatException e)
            {
                /* ignored */
            }

            String communityName = props.getProperty("location.service.snmp.agent.community", "public").trim();

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
                    log.info("SNMP agent started at port " + snmpPort + " with community " + communityName);
            }
            catch(SocketException ex)
            {
                log.error("Cannot start SNMP agent at port " + snmpPort + ": " + ex.getMessage());
            }
        }

        // Default update period - 5 sec.
        int updatePeriod = 5;

        try
        {
            updatePeriod = Integer.parseInt(props.getProperty("location.service.update-period", "5").trim());
        }
        catch (NumberFormatException e)
        {
            // ignored
        }

        // Update period must be > 0
        updatePeriod = Math.max(1, updatePeriod);

        /**
         * Create timer. This timer will check all bindings and delete those that are expired.
         */
        checkBindingsTimer = new Timer(true);

        CheckBindingsTask task = new CheckBindingsTask();

        prevCheckTime = System.currentTimeMillis();

        checkBindingsTimer.schedule(task, 0, updatePeriod * 1000);

        if (log.isInfoEnabled())
            log.info("Bindings refresh period: " + updatePeriod + " seconds.");


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

        if (checkBindingsTimer != null)
            checkBindingsTimer.cancel();

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
     * @return Default domain
     */
    public String getDefaultDomain()
    {
        return defaultDomain;
    }

    /**
     * Returns whether this Location Service is responsible for <i>domain</i>
     * @param domain Domain name
     * @return True if this Location Service is responsible for <i>domain</i>
     */
    public boolean isDomainServed(String domain)
    {
        return domains.contains(domain);
    }

    /**
     * Returns user profile
     * @param key Key to location service
     * @return User profile
     * @throws UserNotFoundException If such user cannot be found
     */
    private UserProfile getProfile(String key) throws UserNotFoundException
    {
        UserProfile userProfile = database.get(key);
        if (userProfile == null) throw new UserNotFoundException(key);
        return userProfile;
    }

    /**
     * @see openjsip.remote.locationservice.LocationServiceInterface
     */
    public HashSet<String> getDomains() throws RemoteException
    {
        return domains;
    }

    /**
     * @see openjsip.remote.locationservice.LocationServiceInterface
     */
    public String getUsername(String key) throws RemoteException, UserNotFoundException
    {
        return getProfile(key).getLogin();
    }

    /**
     * @see openjsip.remote.locationservice.LocationServiceInterface
     */
    public String getPassword(String key) throws RemoteException, UserNotFoundException
    {
        return getProfile(key).getPassword();
    }

    /**
     * @see openjsip.remote.locationservice.LocationServiceInterface
     */
    public synchronized void updateRegistration(String key, ContactHeader contactHeader, long expires, String callId, long cseq) throws RemoteException, UserNotFoundException
    {
        UserProfile userProfile = getProfile(key);

        Binding existingBinding = userProfile.getBinding(contactHeader);
        if (existingBinding != null)
            userProfile.removeBinding(existingBinding);

        Binding binding = new Binding(key, contactHeader, callId, cseq, expires);
        userProfile.addBinding(binding);

        if (log.isDebugEnabled())
            log.debug("Binding updated ( "+key+" ): " +binding.toString());
    }

    /**
     * @see openjsip.remote.locationservice.LocationServiceInterface
     */
    public synchronized void removeBinding(String key, ContactHeader contactHeader) throws RemoteException, UserNotFoundException
    {
        UserProfile userProfile = getProfile(key);

        Binding existingBinding = userProfile.getBinding(contactHeader);
        if (existingBinding != null)
        {
            userProfile.removeBinding(existingBinding);

            if (log.isDebugEnabled())
                log.debug("Binding removed ( " + key + " ): " + existingBinding.toString());
        }
    }

    /**
     * @see openjsip.remote.locationservice.LocationServiceInterface
     */
    public synchronized void removeAllBindings(String key) throws RemoteException, UserNotFoundException
    {
        UserProfile userProfile = getProfile(key);

        userProfile.removeAllBindings();
        
        if (log.isDebugEnabled())
            log.debug("All bindings removed ( "+key+")." );
    }

    /**
     * @see openjsip.remote.locationservice.LocationServiceInterface
     */
    public synchronized Vector<ContactHeader> getContactHeaders(String key) throws RemoteException, UserNotFoundException
    {
        UserProfile userProfile = getProfile(key);

        return userProfile.getContactHeaders();
    }

    /**
     * @see openjsip.remote.locationservice.LocationServiceInterface
     */
    public synchronized Binding getBinding(String key, ContactHeader contactHeader) throws RemoteException, UserNotFoundException
    {
        UserProfile userProfile = getProfile(key);

        return userProfile.getBinding(contactHeader);
    }
   
    /**
     * @see openjsip.remote.RemoteServiceInterface
     */
    public String execCmd(String cmd, String[] parameters) throws RemoteException
    {
        if (cmd == null)
            return null;

        // cmd show
        if (cmd.equalsIgnoreCase("show") && parameters != null)
        {
            if (parameters.length > 0)
            {
                // show bindings
                if (parameters[0].equalsIgnoreCase("bindings"))
                {
                    String subscriber = parameters.length > 1 ? parameters[1] : null;

                    // Subscriber specified
                    if (subscriber != null)
                    {
                        try
                        {
                            UserProfile profile = getProfile(subscriber);
                            Vector<Binding> bindings = profile.getBindings();
                            StringBuffer out = new StringBuffer();

                            for (Binding binding : bindings)
                                out.append(binding.toString()).append("\n");

                            out = out.append("Subscriber " + subscriber + " has " + bindings.size() + " bindings.");
                            return out.toString();
                        }
                        catch(UserNotFoundException ex)
                        {
                            return ex.getMessage();
                        }
                    }
                    // Show all bindings
                    else
                    {
                        Iterator it = database.values().iterator();
                        StringBuffer out = new StringBuffer();
                        int count = 0;

                        while(it.hasNext())
                        {
                            Vector<Binding> bindings = ((UserProfile) it.next()).getBindings();
                            for(Binding binding : bindings)
                            {
                                out.append(binding.toString()).append("\n");
                                count++;
                            }
                        }

                        out = out.append("Database contains " + count + " bindings.");
                        return out.toString();
                    }
                }
                // show subscribers
                else if (parameters[0].equalsIgnoreCase("subscribers"))
                {
                    Set<String> set = database.keySet();
                    StringBuffer out = new StringBuffer();

                    for (String aor : set)
                        out.append(aor + "\n");

                    out = out.append("Database contains " + set.size() + " subscribers.");
                    return out.toString();
                }
            }
        }
        // cmd get
        else if (cmd.equalsIgnoreCase("get") && parameters != null)
        {
            if (parameters.length > 0)
            {
                if (parameters[0].equalsIgnoreCase("numSubscribers"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_SUBSCRIBERS).toString();
                else if (parameters[0].equalsIgnoreCase("numBindings"))
                    return snmpAssistant.getSnmpOIDValue(SNMP_OID_NUM_BINDINGS).toString();
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
        "help                   - Show help.\n" +
        "show subscribers       - Show all subscribers.\n"+
        "show bindings          - Show all existing bindings.\n"+
        "show bindings <AOR>    - Show all bindings of specified subscriber.\n"+
        "get numSubscribers     - Get the total number of subscribers.\n"+
        "get numBindings        - Get the total number of existing bindings.\n"+
        "get vm_freememory      - Get the amount of free memory in the Java Virtual Machine.\n"+
        "get vm_maxmemory       - Get the maximum amount of memory that the Java virtual machine will attempt to use.\n"+
        "get vm_totalmemory     - Get the total amount of memory in the Java virtual machine.\n";

    }

    /**
     * @see openjsip.remote.RemoteServiceInterface
     */
    public boolean isAlive() throws RemoteException
    {
        return true;
    }


    /**
     * Task that runs every "location.service.update-period" seconds and checks
     * all existing bindings for being expired.
     * Expired bindings are removed from user profile.
     * All database update methods are blocked until this task finishes.
     */
    private class CheckBindingsTask extends TimerTask
    {
        public void run()
        {
            // Get monitor ( block other update methods )
            synchronized(LocationService.this)
            {
                long dt = ( System.currentTimeMillis() - prevCheckTime) / 1000;
                prevCheckTime = System.currentTimeMillis();

                Enumeration<UserProfile> profiles = database.elements();

                int numProfiles = 0;
                int numBindings = 0;

                while(profiles.hasMoreElements())
                {
                    UserProfile profile = profiles.nextElement();

                    numProfiles++;

                    Vector bindings = profile.getBindings();

                    numBindings += bindings.size();

                    for (int i = 0; i < bindings.size(); i++)
                    {
                        Binding binding = (Binding) bindings.elementAt(i);

                        // Substract time elapsed from previous update
                        binding.setExpiresTime(binding.getExpiresTime() - dt);

                        // Remove expired binding
                        if (binding.getExpiresTime() <= 0)
                        {
                            profile.removeBinding(binding);

                            i--;

                            if (log.isDebugEnabled())
                                log.debug("Binding expired: "+binding.toString());
                        }
                    }
                }

                SNMPInteger snmpNumSubscribers = snmpAssistant.getSnmpInteger(SNMP_OID_NUM_SUBSCRIBERS);
                SNMPInteger snmpNumBindings = snmpAssistant.getSnmpInteger(SNMP_OID_NUM_BINDINGS);
                try
                {
                    snmpNumSubscribers.setValue(new Integer(numProfiles));
                    snmpNumBindings.setValue(new Integer(numBindings));
                }
                catch (SNMPBadValueException e)
                {
                    e.printStackTrace();
                }
            }

            // Now other datbase methods are allowed to update database.
        }
    }
}
