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

import javax.sip.header.ContactHeader;
import javax.sip.InvalidArgumentException;
import java.io.Serializable;

/**
 * Binding represents association between public address of subscriber
 * and the current IP where it can be contacted.
 */
public class Binding implements Serializable
{
    /**
     * Key to location service database
     */
    private String key;

    /**
     * Contact header that stores the current address of subscriber along with the
     * expires filed that indicated how long this binding will stay valid.
     */
    private ContactHeader contactHeader;

    /**
     * Cached Call-ID value of REGISTER request, by which this Binding was created
     */
    private String callId;

    /**
     * Cached CSeq value of REGISTER request, by which this Binding was created
     */
    private long cseq;

    /**
     * Binding constructor
     * @param key Key to location service database of corresponding subscriber
     * @param contactHeader Contact header that represents the current location of subscriber
     * @param callId Call-ID value of REGISTER request
     * @param cseq CSeq value of REGISTER request
     * @param expiresTime Time in seconds when this binding will expire.
     */
    public Binding(String key, ContactHeader contactHeader, String callId, long cseq, long expiresTime)
    {
        setKey(key);
        setContactHeader(contactHeader);
        setCallId(callId);
        setCseq(cseq);
        setExpiresTime(expiresTime);
    }

    /**
     * @return The key to location database of this subscriber.
     */
    public String getKey()
    {
        return key;
    }

    /**
     * Set the location database key
     * @param key Key to location service database
     */
    public void setKey(String key)
    {
        this.key = key;
    }

    /**
     * @return Contact header that represents the current location of subscriber.
     */
    public ContactHeader getContactHeader()
    {
        return contactHeader;
    }

    /**
     * Set contact header of this binding
     * @param contactHeader Contact header
     */
    public void setContactHeader(ContactHeader contactHeader)
    {
        this.contactHeader = contactHeader;
    }

    /**
     * @return The Call-ID value of REGISTER request that created this binding.
     */
    public String getCallId()
    {
        return callId;
    }

    /**
     * Set the Call-ID value for this binding.
     * @param callId Call-ID
     */
    public void setCallId(String callId)
    {
        this.callId = callId;
    }

    /**
     * @return The CSeq value of REGISTER request that created this binding.
     */
    public long getCseq()
    {
        return cseq;
    }

    /**
     * Set the CSeq value for this binding.
     * @param cseq CSeq
     */
    public void setCseq(long cseq)
    {
        this.cseq = cseq;
    }

    /**
     * @return The remaining lifetime of binding in seconds.
     */
    public long getExpiresTime()
    {
        return contactHeader.getExpires();
    }

    /**
     * Set expire time of this binding
     * @param expiresTime Time in seconds
     */
    public void setExpiresTime(long expiresTime)
    {
        try
        {
            contactHeader.setExpires((int) expiresTime);
        }
        catch (InvalidArgumentException e)
        {
            
        }
    }

    public String toString()
    {
        return contactHeader.toString().trim();
    }
}
