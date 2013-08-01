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

import openjsip.remote.locationservice.Binding;
import openjsip.SipUtils;

import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;
import java.util.Vector;

public class UserProfile
{
    /**
     * Address-of-Record: An address-of-record (AOR) is a SIP or SIPS URI
     * that points to a domain with a location service that can map
     * the URI to another URI where the user might be available.
     * Typically, the location service is populated through
     * registrations.  An AOR is frequently thought of as the "public
     * address" of the user.
     *
     */
    private SipURI addressOfRecord;

    /**
     * The list of bindings of this subscriber.
     */
    private final Vector<Binding> bindings;

    /**
     * Creates user profile with public address <i>addressOfRecord</i> and empty list of current bindings.
     * @param addressOfRecord Public address of subscriber.
     */
    public UserProfile(SipURI addressOfRecord)
    {
        setAddressOfRecord(addressOfRecord);
        bindings = new Vector<Binding>();
    }

    /**
     * @return Public address of subscriber
     */
    public SipURI getAddressOfRecord()
    {
        return addressOfRecord;
    }

    /**
     * Sets public address of subscriber
     * @param addressOfRecord Public address of subscriber
     */
    public void setAddressOfRecord(SipURI addressOfRecord)
    {
        this.addressOfRecord = addressOfRecord;
    }

    /**
     * @return Login (username) of subscriber
     */
    public String getLogin()
    {
        return addressOfRecord.getUser();
    }

    /**
     * @return Password of subscriber, or null if not specified.
     */
    public String getPassword()
    {
        return addressOfRecord.getUserPassword();
    }

    /**
     * @return The current list of bindings of subscriber.
     */
    public Vector<Binding> getBindings()
    {
        return bindings;
    }

    /**
     * @param contactHeader Contact header
     * @return The existing binding whose contact header matches <i>contactHeader</i>
     */
    public Binding getBinding(ContactHeader contactHeader)
    {
        URI uri1 = SipUtils.getCanonicalizedURI(contactHeader.getAddress().getURI());

        for(int i = 0; i < bindings.size(); i++)
        {
            Binding binding = bindings.elementAt(i);
            ContactHeader contactHeader2 = binding.getContactHeader();

            URI uri2 = SipUtils.getCanonicalizedURI(contactHeader2.getAddress().getURI());
            if (uri1.equals(uri2)) return binding;
        }

        return null;
    }

    /**
     * @return The list of contact headers from all bindings of this subscriber.
     */
    public Vector<ContactHeader> getContactHeaders()
    {
        Vector<ContactHeader> contacts = new Vector<ContactHeader>();

        for (Binding binding : bindings)
        {
            contacts.add(binding.getContactHeader());
        }

        return contacts;
    }

    /**
     * Adds the binding to the current list of bindings
     * @param binding Binding to add
     */
    public void addBinding(Binding binding)
    {
        if (!bindings.contains(binding))
            bindings.add(binding);
    }

    /**
     * Removes the specified binding, if found.
     * @param binding Binding to remove.
     */
    public void removeBinding(Binding binding)
    {
        bindings.remove(binding);
    }

    /**
     * Removes all bindings of this subscriber.
     */
    public void removeAllBindings()
    {
        bindings.removeAllElements();
    }

}
