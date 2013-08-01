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
package openjsip.snmp;

import snmp.*;

import java.math.BigInteger;


public class SNMPAssistant implements SNMPRequestListener
{
    private Object[][] SNMP_DATABASE;
    private String communityName;
    
    public SNMPAssistant(String communityName, Object[][] SNMP_DATABASE)
    {
        this.communityName = communityName;
        this.SNMP_DATABASE = SNMP_DATABASE;
    }
    
     /**
     * Handles Get- or Set- request messages. The supplied request PDU may contain multiple OIDs; this
     * method should process those OIDs it understands, and return an SNMPVarBindList containing those OIDs
     * which it has handled and their corresponding values. The order of returned OID-value pairs is not
     * important, as the SNMPv1AgentInterface will order the information appropriately. Each implementer of
     * SNMPRequestListener will likely handle only a subset of the list of supplied OIDs; those OIDs which
     * are not relevant to a particular listener should be ignored, to be handled by another SNMPRequestListener.
     * If any OIDs remain unhandled after all listeners' processRequest() methods have been called, the
     * SNMPv1AgentInterface will return an appropriate error indication to the management entity.
     *
     * @throws snmp.SNMPGetException, SNMPSetException
     *                                If a listener receives a request for an OID which it is intended to handle, but there is a problem with
     *                                the request - e.g., a set-request for a value which is read-only, or an incorrect value type for a set - the
     *                                listener should throw an SNMPGetException or SNMPSetException to indicate the error. The exception should
     *                                include both the index of the OID in the list of supplied OIDs, as well as an error status code (status values
     *                                are provided as constants in the SNMPRequestException class definition). The SNMPRequestException class and
     *                                subclasses provide constructors allowing the specification of the error index and status code. Note that the
     *                                error index follows the SNMP convention of starting at 1, not 0: thus if there is a problem with the first OID,
     *                                the error index should be 1. The SNMPAgentInterface will use the information in the exception to communicate
     *                                the error to the requesting management entity. The community name should also be used to determine if a request
     *                                is valid for the supplied community name.
     */

    public SNMPSequence processRequest(SNMPPDU requestPDU, String communityName) throws SNMPGetException, SNMPSetException
    {
        SNMPSequence varBindList = requestPDU.getVarBindList();
        SNMPSequence responseList = new SNMPSequence();
        byte pduType = requestPDU.getPDUType();

        for (int i = 0; i < varBindList.size(); i++)
        {
            SNMPSequence variablePair = (SNMPSequence )varBindList.getSNMPObjectAt(i);
            SNMPObjectIdentifier snmpOID = (SNMPObjectIdentifier) variablePair.getSNMPObjectAt(0);

            // check to see if supplied community name is ours; if not, we'll just silently
            // ignore the request by not returning anything
            if (!communityName.equals(this.communityName))
            {
                continue;
            }

            if (pduType == SNMPBERCodec.SNMPGETREQUEST)
            {
                SNMPObject value = getSnmpOIDValue(snmpOID.toString());
                if (value != null)
                {
                    try
                    {
                        SNMPVariablePair newPair = new SNMPVariablePair(snmpOID, value);
                        responseList.addSNMPObject(newPair);
                    }
                    catch (SNMPBadValueException e)
                    {

                    }
                }
            }

            if (pduType == SNMPBERCodec.SNMPSETREQUEST)
            {
                // not used yet
            }
        }

        // return the created list of variable pairs
        return responseList;
    }

    /**
     * Handles Get-Next- request messages. The supplied request PDU may contain multiple OIDs; this
     * method should process those OIDs it understands, and return an SNMPVarBindList containing special
     * variable pairs indicating those supplied OIDs which it has handled, i.e., it must indicate a
     * supplied OID, the "next" OID, and the value of this next OID. To do this, the return value is a
     * sequence of SNMPVariablePairs, in which the first component - the OID - is one of the supplied OIDs,
     * and the second component - the value - is itself an SNMPVariablePair containing the "next" OID and
     * its value:
     * <p/>
     * return value = sequence of SNMPVariablePair(original OID, SNMPVariablePair(following OID, value))
     * <p/>
     * In this way the SNMPv1AgentInterface which calls this method will be able to determine which of the
     * supplied OIDs each "next" OID corresponds to.
     * <p/>
     * The order of returned "double" OID-(OID-value) pairs is not important, as the SNMPv1AgentInterface
     * will order the information appropriately in the response. Each implementer of
     * SNMPRequestListener will likely handle only a subset of the list of supplied OIDs; those OIDs which
     * are not relevant to a particular listener should be ignored, to be handled by another SNMPRequestListener.
     * If any OIDs remain unhandled after all listeners' processRequest() methods have been called, the
     * SNMPv1AgentInterface will return an appropriate error indication to the management entity.
     *
     * @throws snmp.SNMPGetException If a listener receives a request for an OID which it is intended to handle, but there is a problem with
     *                               the request - e.g., a get-next request for a value which is not readable for the supplied community name -
     *                               the listener should throw an SNMPGetException to indicate the error. The exception should
     *                               include both the index of the OID in the list of supplied OIDs, as well as an error status code (status values
     *                               are provided as constants in the SNMPRequestException class definition). The SNMPRequestException class and
     *                               subclasses provide constructors allowing the specification of the error index and status code. Note that the
     *                               error index follows the SNMP convention of starting at 1, not 0: thus if there is a problem with the first OID,
     *                               the error index should be 1. The SNMPAgentInterface will use the information in the exception to communicate
     *                               the error to the requesting management entity. The community name should also be used to determine if a request
     *                               is valid for the supplied community name.
     */

    public SNMPSequence processGetNextRequest(SNMPPDU requestPDU, String communityName) throws SNMPGetException
    {
        SNMPSequence varBindList = requestPDU.getVarBindList();
        SNMPSequence responseList = new SNMPSequence();
        byte pduType = requestPDU.getPDUType();

        for (int i = 0; i < varBindList.size(); i++)
        {
            SNMPSequence variablePair = (SNMPSequence )varBindList.getSNMPObjectAt(i);
            SNMPObjectIdentifier snmpOID = (SNMPObjectIdentifier) variablePair.getSNMPObjectAt(0);
            String snmpOIDStr = snmpOID.toString();

            // check to see if supplied community name is ours; if not, we'll just silently
            // ignore the request by not returning anything
            if (!communityName.equals(this.communityName))
            {
                continue;
            }

            if (pduType == SNMPBERCodec.SNMPGETNEXTREQUEST)
            {
                int index;

                for (index = 0; index < SNMP_DATABASE.length; index++)
                {
                    String oid = (String) SNMP_DATABASE[index][0];
                    if (oid.startsWith(snmpOIDStr))
                        break;
                }

                if (index < SNMP_DATABASE.length - 1)
                {
                    if (snmpOIDStr.length() == ((String) SNMP_DATABASE[index][0]).length())
                        index++;

                    try
                    {
                        if (index < SNMP_DATABASE.length)
                        {
                            String nextOIDStr = (String) SNMP_DATABASE[index][0];
                            // create SNMPVariablePair for the next OID and its value
                            SNMPObjectIdentifier nextOID = new SNMPObjectIdentifier(nextOIDStr);
                            SNMPVariablePair innerPair = new SNMPVariablePair(nextOID, getSnmpOIDValue(nextOIDStr));

                            // now create a pair containing the supplied OID and the variable pair containing the following
                            // OID and its value; this allows the ANMPv1AgentInterface to know which of the supplied OIDs
                            // the new OID corresponds to (follows).
                            SNMPVariablePair outerPair = new SNMPVariablePair(snmpOID, innerPair);

                            // add the "compound" SNMPVariablePair to the response list
                            responseList.addSNMPObject(outerPair);
                        }
                    }
                    catch (SNMPBadValueException e)
                    {
                        e.printStackTrace();
                    }

                }
            }
        }

        return responseList;
    }


    public synchronized SNMPInteger getSnmpInteger(String oid)
    {
        return getSnmpInteger(oid, 0);
    }

    public synchronized SNMPInteger getSnmpInteger(String oid, int defaultValue)
    {
        SNMPInteger value = (SNMPInteger) getSnmpOIDValue(oid);
        if (value == null) value = new SNMPInteger(defaultValue);

        return value;
    }

     public synchronized void setSnmpInteger(String oid, int newValue)
    {
        SNMPInteger value = (SNMPInteger) getSnmpOIDValue(oid);
        if (value != null)
            try
            {
                value.setValue(new Integer(newValue));
            }
            catch (SNMPBadValueException e)
            {

            }
    }

    public synchronized void incrementSnmpInteger(String oid)
    {
        try
        {
            SNMPInteger value = getSnmpInteger(oid, 0);
            value.setValue(((Number) value.getValue()).intValue() + 1);
        }
        catch (SNMPBadValueException e)
        {
            /* ignored */
        }
    }

    public synchronized void decrementSnmpInteger(String oid)
    {
        try
        {
            SNMPInteger value = getSnmpInteger(oid, 0);
            value.setValue(((Number) value.getValue()).intValue() - 1);
        }
        catch (SNMPBadValueException e)
        {
            /* ignored */
        }
    }

    public int findSnmpOID(String oidToFind)
    {
        for (int i = 0; i < SNMP_DATABASE.length; i++)
        {
            Object oid = SNMP_DATABASE[i][0];
            if (oid.equals(oidToFind))
                return i;
        }

        return -1;
    }

    public SNMPObject getSnmpOIDValue(String oid)
    {
        int index = findSnmpOID(oid);
        return index != -1 ? (SNMPObject) SNMP_DATABASE[index][1] : null;
    }
}
