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
package openjsip.remote.locationservice;

import openjsip.remote.locationservice.UserNotFoundException;
import openjsip.remote.locationservice.Binding;
import openjsip.remote.RemoteServiceInterface;

import javax.sip.header.ContactHeader;
import java.rmi.RemoteException;
import java.util.Vector;
import java.util.HashSet;

public interface LocationServiceInterface extends RemoteServiceInterface
{
    /**
     * @param key Key to location service directory
     * @return Returns the username of specified subscriber
     * @throws RemoteException
     * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
     */
    public String getUsername(String key) throws RemoteException, UserNotFoundException;

    /**
     * @param key Key to location service directory
     * @return Returns the password of specified subscriber, or empty string if unspecified
     * @throws RemoteException
     * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
     */
    public String getPassword(String key) throws RemoteException, UserNotFoundException;

    /**
     * Create or update binding for subscriber.
     * @param key Key to location service directory
     * @param contactHeader Contact address that is associated with the subscriber <i>key</i> for the duration of <i>expires</i>
     * @param expires Time-to-live value for this binding measured in seconds.
     * @param callId CallID parameter from REGISTER request
     * @param cseq CSeQ parameter from REGISTER request
     * @throws RemoteException
     * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
     */
    public void updateRegistration(String key, ContactHeader contactHeader, long expires, String callId, long cseq) throws RemoteException, UserNotFoundException;

    /**
     * Removes one contact address binding of subscriber.
     * @param key Key to location service directory
     * @param contactHeader Contact address that is associated with the subscriber <i>key</i>.
     * @throws RemoteException
     * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
     */
    public void removeBinding(String key, ContactHeader contactHeader) throws RemoteException, UserNotFoundException;

    /**
     * Removes all contact address bindings of subscriber.
     * @param key Key to location service directory
     * @throws RemoteException
     * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
     */
    public void removeAllBindings(String key) throws RemoteException, UserNotFoundException;

    /**
     * @param key Key to location service directory
     * @return Returns all contact addresses associated with the subscriber.
     * @throws RemoteException
     * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
     */
    public Vector<ContactHeader> getContactHeaders(String key) throws RemoteException, UserNotFoundException;

    /**
     * @param key Key to location service directory
     * @param contactHeader Contact address that is associated with the subscriber <i>key</i>.
     * @return Returns the binding of subscriber whose contact address matches <i>contactHeader</i>, null otherwise.
     * @throws RemoteException
     * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
     */
    public Binding getBinding(String key, ContactHeader contactHeader) throws RemoteException, UserNotFoundException;


    /**
     * @return The list of domains location service has provisioned subscribers
     * @throws RemoteException
     */
    public HashSet<String> getDomains() throws RemoteException;

    /**
     * @return Default domain
     * @throws RemoteException
     */
    public String getDefaultDomain() throws RemoteException;

}
