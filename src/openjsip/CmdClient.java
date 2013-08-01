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
package openjsip;

import openjsip.remote.RemoteServiceInterface;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.rmi.NotBoundException;
import java.util.Vector;
import java.util.Arrays;

/**
 * Command-line client is experimental feature.
 */
public class CmdClient
{
    private static final int GET = 0;
    private static final int SET = 1;
    private static final int PING = 2;
    private static final int CUSTOM = 3;

    private int cmd;

    /**
     * RMI connection settings
     */
    private String RMIHost, RMIObjectName;
    private int RMIPort;
    
    /**
     * Options
     */
    // Do not separate results by newline
    private boolean optionNoNewLine = false;
    // Print "NaN" instead of "Null". Used with RRDTool.
    private boolean optionPrintNullAsNaN = false;
    // Do not print keys when printing results as key:value
    private boolean optionDoNotPrintKeys = false;
    // Separate result with : instead of space when using -r option.
    private boolean optionUseColon = false;


    private boolean firstPrint = true;

    /**
     * Entry point
     * @param args
     */
    public static void main(String[] args)
    {
        try
        {
            new CmdClient(args);
        }
        catch (IllegalArgumentException ex)
        {
            System.err.println("Error: "+ex.getMessage());
            printHelp();
            System.exit(1);
        }
        catch (RemoteException ex)
        {
            System.err.println("Error: Cannot connect to RMI registry at target host and port.");
            System.exit(1);
        }
         catch (NotBoundException ex)
        {
            System.err.println("Error: RMI registry is running but remote service was not found in it. Check if server is running and registered within RMI registry at target host and port.");
            System.exit(1);
        }
    }

    /**
     * Prints help
     */
    private static void printHelp()
    {
        System.out.println("\nSyntax: cmdclient [options] rmi://rmihost:rmiport/servicename <cmd> [ parameter 1 ... parameter n ]\n"+
                           "Where <cmd> can be:\n"+
                           "  get         Get property. For list of all properties use command 'help'.\n"+
                           "              Addtitonal output options are used for this type of command.\n"+
                           "              See description below.\n"+
                           //"  set         Set property. Parameters must be in the form key=value\n"+
                           "  ping        Check if remote service is alive.\n" +
                           "  help        Ask remote service to print its help.\n"+
                           "\nOptions for 'get' command:\n"+
                           " -r          Print results in a row.\n"+
                           " -n          Do not print 'null', print 'nan' instead.\n"+
                           " -k          Do not print keys, print values only.\n"+
                           " -c          Use colon ':' instead of space when delimiting results.\n"

        );
    }


    /**
     * Constructor
     * @param cmdArgs Command line arguments
     * @throws IllegalArgumentException Not enough / illegal command-line arguments
     * @throws RemoteException Cannot connect to RMI registry
     * @throws NotBoundException Remote service is not running
     */
    private CmdClient(String[] cmdArgs) throws IllegalArgumentException, RemoteException, NotBoundException
    {
        if (cmdArgs.length < 2)
            throw new IllegalArgumentException("Not enough arguments...");

        // Convert array of arguments to vector
        Vector<String> args = new Vector<String>(Arrays.asList(cmdArgs));

        while (args.firstElement().startsWith("-"))
        {
            parseParameter(args.firstElement());
            args.removeElementAt(0);
        }

        // Parse RMI URI
        if (!args.firstElement().startsWith("rmi://"))
            throw new IllegalArgumentException("Remote service connection URI must be started with rmi://");

        args.setElementAt(args.firstElement().substring("rmi://".length()), 0);

        int index = args.elementAt(0).indexOf(':');
        if (index == -1) throw new IllegalArgumentException();

        RMIHost = args.firstElement().substring(0, index);

        int index2 = args.elementAt(0).indexOf('/');
        if (index2 == -1) throw new IllegalArgumentException();

        String RMIPortStr = args.firstElement().substring(index+1, index2);

        RMIPort = Integer.parseInt(RMIPortStr);

        RMIObjectName = args.firstElement().substring(index2+1);

        if (RMIObjectName.length() == 0)
            throw new IllegalArgumentException();

        // Shift arguments
        args.removeElementAt(0);

        // Parse command type
        if (args.firstElement().equalsIgnoreCase("get"))
            cmd = GET;
        //else if (args.firstElement().equalsIgnoreCase("set"))
        //    cmd = SET;
        else if (args.firstElement().equalsIgnoreCase("ping"))
            cmd = PING;
        else
            cmd = CUSTOM;

        switch(cmd)
        {
            case GET:
                
                // ensure we have something to get
                if (args.size() < 2)
                    throw new IllegalArgumentException("Get what ? Please specify, or type help.");

                // Get remote service instance
                RemoteServiceInterface remoteService = getRemoteServiceInterface();

                // Move through each parameter and execute a 'get' command
                for (int i = 1; i < args.size(); i++)
                {
                    String parameter = args.elementAt(i);
                    String result = remoteService.execCmd("get", new String[] { parameter } );

                    // Adapt result according to set options
                    if (result == null && optionPrintNullAsNaN) result = "nan";

                    if (optionNoNewLine)
                    {
                        if (!firstPrint)
                            System.out.print(optionUseColon ? ":" : " ");

                        System.out.print((optionDoNotPrintKeys ? "" : (parameter + ":")) + result);
                    }
                    else
                        System.out.println((optionDoNotPrintKeys ? "" : (parameter + ":")) + result);

                    firstPrint = false;
                }
                break;

            case PING:
                try
                {
                    getRemoteServiceInterface().isAlive();
                    System.out.println(RMIObjectName+" is alive.");
                }
                catch (Exception e)
                {
                    System.out.println(RMIObjectName+" is dead.");
                }
                break;

            case CUSTOM:
                // Get remote service instance
                remoteService = getRemoteServiceInterface();

                String[] parameters = null;

                if (args.size() > 1)
                {
                    parameters = new String[ args.size() - 1];
                    args.subList(1, args.size()).toArray(parameters);
                }

                String result = remoteService.execCmd(args.firstElement(), parameters);
                System.out.println(result);
                break;
        }
    }

    /**
     * Parses command-line parameters.
     * @param parameter Parameter string. It can combine several parameters. For example two parameters '-r' and '-n' can be combined as '-rn'
     */
    private void parseParameter(String parameter)
    {
        StringBuffer buf = new StringBuffer(parameter);
        if (buf.charAt(0) == '-') buf.deleteCharAt(0);

        for (int i=0; i<buf.length(); i++)
            parseParameter(buf.charAt(i));
    }

    /**
     * Parses single parameter
     * @param ch Single parameter like 'r'. 
     */
    private void parseParameter(char ch)
    {
        switch(ch)
        {
            case 'r':
                optionNoNewLine = true;
                break;

            case 'n':
                optionPrintNullAsNaN = true;
                break;

            case 'k':
                optionDoNotPrintKeys = true;
                break;

            case 'c':
                optionUseColon = true;
                break;
        }
    }

    /**
     * @return Remote service instance, or null if unavailable
     */
    private RemoteServiceInterface getRemoteServiceInterface() throws RemoteException, NotBoundException
    {
        Registry registry = LocateRegistry.getRegistry(RMIHost, RMIPort);
        return (RemoteServiceInterface) registry.lookup(RMIObjectName);
    }
}
