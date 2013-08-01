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

import javax.sip.ServerTransaction;
import javax.sip.ClientTransaction;
import javax.sip.SipProvider;
import java.util.*;

/**
 * When stateful, a proxy is purely a SIP transaction processing engine.
 * Its behavior is modeled here in terms of the server and client
 * transactions defined in Section 17.  A stateful proxy has a server
 * transaction associated with one or more client transactions by a
 * higher layer proxy processing component (see figure 3), known as a
 * proxy core.
 */

/**
 * This is a class that maps ServerTransactions to one or more ClientTransactions
 */
public class TransactionsMapping
{
    /**
     * SipProvider
     */
    private final SipProvider sipProvider;

    /**
     * Associated with the request ServerTransaction
     */
    private final ServerTransaction serverTransaction;

    /**
     * Set of ClientTransactions
     */
    private final HashSet<ClientTransaction> clientTransactions;

    /**
     * Timer C to ClientTransaction mapping
     */
    private final Hashtable<ClientTransaction, Timer> timers;

    /**
     * Response context
     */
    private final ResponseContext responseContext;

    /**
     * Constructs a new transactions mapping class
     * @param serverTransaction ServerTransaction object for wich this class will hold associated ClientTransactions
     * @param sipProvider SipProvider object wich received request and with wich <i>serverTransaction</i> is associated.
     * @throws NullPointerException If <i>serverTransaction<i/> is null
     */
    public TransactionsMapping(ServerTransaction serverTransaction, SipProvider sipProvider)
    {
        serverTransaction.getRetransmitTimer();
        this.sipProvider = sipProvider;
        this.clientTransactions = new HashSet<ClientTransaction>();
        this.serverTransaction = serverTransaction;
        this.timers = new Hashtable<ClientTransaction, Timer>();
        this.responseContext = new ResponseContext(serverTransaction.getRequest());
    }

    public ServerTransaction getServerTransaction()
    {
        return serverTransaction;
    }

    /**
     * @return Client transactions associated with server transactions. If none, the empty set is returned (but not null).
     */
    public HashSet getClientTransactions()
    {
        return clientTransactions;
    }

    /**
     * @return Client transactions associated with server transactions. If none, zero-length array is returned (but not null).
     */
    public ClientTransaction[] getClientTransactionsArray()
    {
        ClientTransaction[] cts = new ClientTransaction[clientTransactions.size()];
        clientTransactions.toArray(cts);
        return cts;
    }

    public SipProvider getSipProvider()
    {
        return sipProvider;
    }

    public boolean hasClientTransactions()
    {
        return !clientTransactions.isEmpty();
    }

    public boolean contains(ClientTransaction clientTransaction)
    {
        return clientTransactions.contains(clientTransaction);
    }

    public void addClientTransaction(ClientTransaction clientTransaction)
    {
        clientTransactions.add(clientTransaction);
    }

    public void removeMapping(ClientTransaction clientTransaction)
    {
        this.clientTransactions.remove(clientTransaction);
	}

    /**
     * Register TimerC for the <i>clientTransaction</i>.
     * TimerC is used to terminate clientTransaction when the final response is absent for a long time.
     * @param timer Timer object
     * @param clientTransaction Client transaction which timer will cancel.
     */
    public void registerTimerC(Timer timer, ClientTransaction clientTransaction)
    {
        if (timer != null && !contains(clientTransaction))
        {
            timers.put(clientTransaction, timer);
        }
    }

    /**
     * Cancels and removes TimerC to ClientTransaction association.
     * @param clientTransaction Client transaction object for which to cancel TimerC.
     */
    public void cancelTimerC(ClientTransaction clientTransaction)
    {
        Timer timer = timers.get(clientTransaction);
        if (timer != null)
        {
            timer.cancel();
            timers.remove(clientTransaction);
        }
    }

    public ResponseContext getResponseContext()
    {
        return responseContext;
    }

}
