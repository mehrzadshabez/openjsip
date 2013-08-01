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
package openjsip.remote;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteServiceInterface extends Remote
{
    /**
     * Executes internal command. It is used by command-line client.
     * Client may ask service to get help on all commands by sending cmd 'help'.
     * @param cmd Command to execute
     * @param parameters Parameters if any
     * @return Result
     */
    public String execCmd(String cmd, String[] parameters) throws RemoteException;

    /**
     * @return true if remote service is alive
     */
    public boolean isAlive() throws RemoteException;
}
