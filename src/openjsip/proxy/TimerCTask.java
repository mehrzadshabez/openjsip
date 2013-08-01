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

import javax.sip.ClientTransaction;
import javax.sip.TransactionState;
import javax.sip.SipProvider;
import javax.sip.SipException;
import javax.sip.message.Request;
import java.util.TimerTask;


public class TimerCTask extends TimerTask
{
    private ClientTransaction clientTransaction;
    private SipProvider sipProvider;
    private Logger log;
    private Proxy proxy;

    public TimerCTask(ClientTransaction clientTransaction, SipProvider sipProvider, Proxy proxy, Logger logger)
    {
        this.clientTransaction = clientTransaction;
        this.sipProvider = sipProvider;
        this.proxy = proxy;
        this.log = logger;
    }

    public void run()
    {
        try
        {
            if (log.isTraceEnabled())
                log.trace("Timer C for CT " + clientTransaction + " fired.");

            /**
             * 16.8 Processing Timer C
             *
             * If timer C should fire, the proxy MUST either reset the timer with
             * any value it chooses, or terminate the client transaction.  If the
             * client transaction has received a provisional response, the proxy
             * MUST generate a CANCEL request matching that transaction.  If the
             * client transaction has not received a provisional response, the proxy
             * MUST behave as if the transaction received a 408 (Request Timeout)
             * response.
             * Allowing the proxy to reset the timer allows the proxy to dynamically
             * extend the transaction's lifetime based on current conditions (such
             * as utilization) when the timer fires.
             */

            if (clientTransaction.getState().getValue() == TransactionState._PROCEEDING)
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

                Request cancelRequest = clientTransaction.createCancel();
                ClientTransaction cancelTransaction = sipProvider.getNewClientTransaction(cancelRequest);

                proxy.getSnmpAssistant().decrementSnmpInteger(Proxy.SNMP_OID_NUM_CLIENT_TRANSACTIONS);

                cancelTransaction.sendRequest();

                if (log.isTraceEnabled())
                    log.trace("Timer C dispatched CANCEL request for CT " + clientTransaction);
            }
            else if (log.isTraceEnabled())
                log.trace("Timer C associated CT is not in PROCEEDING state: "+clientTransaction.getState()+". Do nothing.");
        }
        catch (SipException ex)
        {
            if (log.isDebugEnabled())
                log.debug("Exception raised while processing Timer C action: "+ex.getMessage());
            if (log.isTraceEnabled())
                log.trace("Exception raised while processing Timer C action: "+ex.getMessage(), ex);
        }
    }
}
