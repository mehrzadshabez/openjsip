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
package openjsip.remote.registrar;

import openjsip.remote.RemoteServiceInterface;

import javax.sip.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.rmi.RemoteException;

public interface RegistrarInterface extends RemoteServiceInterface
{
    /**
     * Processes REGISTER requests
     * @param request Request
     * @param correctDomain If set, registrar will use this value instead that one in request itself. This is used when request contains IP address instead of domain name.
     * @return Response to REGISTER request
     * @throws ParseException
     * @throws InvalidArgumentException
     * @throws RemoteException
     */
    public Response processRequest(Request request, String correctDomain) throws ParseException, InvalidArgumentException, RemoteException;
}