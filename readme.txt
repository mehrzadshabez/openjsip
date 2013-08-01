  Open Java SIP project (OpenJSIP)
 ----------------------------------
 http://code.google.com/p/openjsip/
 
----------------------------------- 
            R E A D M E
-----------------------------------

1. About
2. Current development status
3. License
4. How to install and use
5. Directory layout
6. Quick start
7. Development hints
8. Troubleshooting
9. Contacts


1. About
----------

OpenJSIP is a GNU GPL licensed bundle of free distributed SIP services ( Proxy, Registrar and so on) run by Java VM.
Distributed means that these components can be deployed on different hosts and communicating 
with each other with the help of Remote Method Invocation (RMI, see java.sun.com for details).
It was started under inspiration of JAIN-SIP project ( full implementation of RFC 3261 for Java ).


If you don't understand what is it all about, please look through the following intro.

This project is about SIP protocol, which allows us to contact each other with the help of VOIP.
To make the things work there are services that do a lot of usefull things. Some of them
keep tracking of subscriber's current location (IP addresses), the other ones do speech codec
conversions and so on.

Here are the basic entities with description copied from RFC3261:

User Agent Client (UAC): A user agent client is a logical entity
         that creates a new request, and then uses the client
         transaction state machinery to send it.  The role of UAC lasts
         only for the duration of that transaction.  In other words, if
         a piece of software initiates a request, it acts as a UAC for
         the duration of that transaction.  If it receives a request
         later, it assumes the role of a user agent server for the
         processing of that transaction.

User Agent Server (UAS): A user agent server is a logical entity
         that generates a response to a SIP request.  The response
         accepts, rejects, or redirects the request.  This role lasts
         only for the duration of that transaction.  In other words, if
         a piece of software responds to a request, it acts as a UAS for
         the duration of that transaction.  If it generates a request
         later, it assumes the role of a user agent client for the
         processing of that transaction.

Proxy, Proxy Server: An intermediary entity that acts as both a
         server and a client for the purpose of making requests on
         behalf of other clients.  A proxy server primarily plays the
         role of routing, which means its job is to ensure that a
         request is sent to another entity "closer" to the targeted
         user.  Proxies are also useful for enforcing policy (for
         example, making sure a user is allowed to make a call).  A
         proxy interprets, and, if necessary, rewrites specific parts of
         a request message before forwarding it.

Registrar: A registrar is a server that accepts REGISTER requests
         and places the information it receives in those requests into
         the location service for the domain it handles.

Location Service: A location service is used by a SIP redirect or
         proxy server to obtain information about a callee's possible
         location(s).  It contains a list of bindings of address-of-
         record keys to zero or more contact addresses.  The bindings
         can be created and removed in many ways; this specification
         defines a REGISTER method that updates the bindings.

Back-to-Back User Agent: A back-to-back user agent (B2BUA) is a
         logical entity that receives a request and processes it as a
         user agent server (UAS).  In order to determine how the request
         should be answered, it acts as a user agent client (UAC) and
         generates requests.  Unlike a proxy server, it maintains dialog
         state and must participate in all requests sent on the dialogs
         it has established.  Since it is a concatenation of a UAC and
         UAS, no explicit definitions are needed for its behavior.



In two words, when you call someone - you are UAC, and your friend is UAS.
And what actually do Proxy ? For example, you don't know the current IP address of
your friend. Proxy finds for you his IP address and routes your SIP messages to him and 
vice versa. To find the current IP of your friend proxy contacts Location Service.
Location Service is itself filled with contact data by Registrar. When your friend goes online, 
he sends its credentials to Registrar. Registrar will then upload data to Location Service, 
so Proxy will be able then to find him.


So, at the current stage of development the following services where implemented in OpenJSIP:

  * SIP Proxy
  * SIP Registrar
  * SIP Location Service


As it was already said these services are distributed. Distributed architecture allows building the
redundant and load-balanced systems.

Q. What is the difference between for example Asterisk and OpenJSIP ?
A. Asterisk is a monolith B2BUA with built in proxy, registrar and location service functionalities.
   Asterisk can convert speech codecs, can also act like Voice Mailbox and so on..
   OpenJSIP for now is just a simple SIP Proxy which routes SIP messages between subscribers, 
   and have no relationship to voice data. Voice data flows between two subscribers directly.


2. Current development status
------------------------------

Latest release v0.0.4
Latest release description: Draft, but we are working... )))

The following services implemented:

   * SIP Proxy
	- Statefull mode
	- Stateless mode
	- Optional processing of REGISTER requests

   * SIP Registrar
	- Standalone mode ( listen's for REGISTER requests itself )
	- ViaProxy mode	( REGISTER requests are delivered by proxy via RMI )

   * SIP Location Service
	- No database integration yet. ( Keeps data in memory ).
    

3. License
-----------

OpenJSIP is a GNU GPL licensed project. GPL is copyrighted by the Free Software Foundation 
and the OpenJSIP software is mainly copyrighted by Yevgen Krapiva (for now).
The GPL applies to this copy of OpenJSIP (Open Java SIP) software.
You should have received a copy of the GNU General Public License along with this project.
If not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

This project also uses 3rd party tools that are licensed differently.
To use and redistribute this copy of OpenJSIP distributive, you must agree to the license terms of these tools:

 * Java SNMP Package by Jonathan Sevy ( http://gicl.cs.drexel.edu/people/sevy/snmp/ ).
   License: free.
 * Apache Log4J library ( http://logging.apache.org/log4j/1.2/ ).
   License: Apache License 2.0. See license.apache file.
   

4. How to install and use
--------------------------

Please make attention that no graphical user interface is shipped with this project (for now).
Are you scared ? No ? Then let's go !

You should have installed Java Runtime Environment version 1.6.0 or later.
Make sure that path to java VM executable is in the PATH variable.

Note:
You also need to run rmiregistry tool before using OpenJSIP components.
This tool is located at JRE_HOME/bin directory. RMI cannot be used without it.

 * UNIX
  1. Extract the archive to some location.
  2. Run bin/openjsip script and follow instructions.
  
  Example:
     > rmiregistry &
     > cd /tmp/openjsip
     > bin/openjsip start location-service 
     > bin/openjsip start registrar
     > bin/openjsip start proxy
     
 * Windows
 1. Extract the archive to some location
 2. Edit with text editor %openjsip_installation_dir%/bin/openjsip.bat file
 3. At the top of the file set the correct value of base_dir variable pointing to the %openjsip_installation_dir%
 4. Run bin\openjsip.bat script and follow instructions.

  Example:
     C:\openjsip> start rmiregistry
     C:\openjsip> start bin\openjsip.bat start location-service
     C:\openjsip> start bin\openjsip.bat start registrar
     C:\openjsip> start bin\openjsip.bat start proxy

Start every component in the order it is shown. Note that every component can be restarted with no influence to the others.
But if you restart location service, it will loose all its data. Because it works like this at the current stage of development.

With version 0.0.3 we ship additional EXPERIMENTAL tool called command-line client. This client can be used to query services
for various data (like counters). Initially it was in mind to use it for gathering counters from services and draw graphs.
But now we plan to use SNMP for such task. So beginning with version 0.0.3 there is also EXPERIMENTAL SNMP Agent.
SNMP Agents are configured in configuration files of each service separately.

Now I will show you how to use command-line client and SNMP.

 * UNIX 	

	Start some service, Location Service for example:
	> rmiregistry &
	> cd /tmp/openjsip
	> bin/openjsip start location-service

	During startup of service you will see the following message:
          ...
          ... Location Service registered as "LocationService" within RMI registry at localhost:1099
          ...

	Use this data to construct RMI URI and issue a command.

        > bin/cmdclient rmi://localhost:1099/LocationService help

		You will get help from Location Service server.

        > bin/cmdclient rmi://localhost:1099/LocationService show subscribers
	  sip:3000@openjsip.net
	  sip:user1@openjsip.net
	  sip:2000@openjsip.net
	  sip:1000@openjsip.net
	  sip:user3@openjsip.net
	  sip:user2@openjsip.net
	  Database contains 6 subscribers.

	During startup of service you can also see the following message:
          ...
          ... SNMP agent started at port 1161 with community public
          ...
	
	> snmpwalk -c public -v1 -On localhost:1161 1.3.6.1
		.1.3.6.1.4.1.1937.1.1.1 = Gauge32: 6
		.1.3.6.1.4.1.1937.1.1.2 = Gauge32: 0

	No MIBs are ready yet. For description of OIDs please refer to source code.


 * Windows

	Windows users, please follow the steps described for unix users.
	And note that RMI URI for unix ( rmi://localhost:1099/LocationService )
	is the same for windows ( do not change backslashes ).


Any questions ? Please refer to Contacts section.


5. Directory layout
--------------------

/

  bin/			- Contains binaries used for starting, stopping and management of services.
        openjsip	- Unix shell executable script.
	openjsip.bat	- Windows executable script.
	cmdclient	- Unix shell executable script for operation and management of OpenJSIP services.
	cmdclient.bat	- Windows executable script for operation and management of OpenJSIP services.

  conf/
	location-service.properties	- Configuration file for SIP Location Service service.
	registrar.properties		- Configuration file for SIP Registrar service.
	proxy.properties		- Configuration file for SIP Proxy service.
	users.properties		- Subscribers database

  docs/			- Contains different documentaion on OpenJSIP source code and other things. 
			  You can also find RFC3261 here.

  extra/		- Contains different tools, such as tools for collecting statistics. See additional readme there.

  lib/			- Contains different libraries required by OpenJSIP and OpenJSIP .jar itself.

  logs/			- Contains logs.

  policy/		- Contains RMI policy files.

  src/			- Contains OpenJSIP source files.

  
6. Quick start
---------------

OpenJSIP default config files assume that Location Service, Registrar and Proxy will run
on the local machine all together. You do not need to modify anything.

Just go to OpenJSIP installation directory and run all these services.
Don't forget about rmiregistry tool. See examples at section 4 of this readme.	
If every component has successfully started, you may now configure your phones to set up 
correct proxy ip address and port and try to call each other.
Look at conf/users.properties file to see, add or configur subscribers profiles.


7. Development hints
---------------------

This project is shipped with the Ant build file (build.xml). To use it you should have
installed Apache Ant (http://ant.apache.org/). Apache Ant is a Java-based build tool.
To compile a project go to the directory where OpenJSIP is installed and run ant
from a command line.

Example:
    > cd /tmp/openjsip
    > ant
    
Ant by default will compile all sources located at src directory and will make openjsip.jar
and openjsip-remote.jar files under lib directory.

Note:
openjsip-remote.jar file contains only interfaces and classes for developing RMI clients and
RMI servers. And openjsip.jar file contains services implementations. So, for example, if you need
to create some client to OpenJSIP service, you create a separate project, import openjsip-remote.jar
library and start developing.
Ant build.xml file contains tasks that create these two files.

To view all available tasks, type ant -projecthelp, for example:

> cd /tmp/openjsip
> ant -projecthelp
Buildfile: build.xml

Main targets:

 build.all                Build all including Java API docs.
 build.javadoc            Build API documentaion.
 build.project.library    Make project library
 build.remote.library     Make remote interfaces library
 clean.all                Cleanup All
 clean.output.dir         Cleanup output dir (where temporary classes are stored)
 compile.project.library  Compile project library
 compile.remote.library   Compile remote interfaces library
Default target: build.all


If you wish to participate on a project, please guide the following rules:

 * Do not hustle the code
 * Keep things compact and clear
 * Do not be lazy of commenting out the code. Quotes from official documents like RFC 
   are preferred.
 * Do not generate a lot of functions. If some block of code is used only in one place then
   don't form it as function. Less the functions the better it would be to read the code.


Hints on viewing log files:

Log viewer tool is not created yet. Because of all requests are processed simultaneously,
it is quite hard to filter messages of specific call.
For this reason, we place Call-ID value in each log message. To filter log files by specific Call-ID
use 'grep' (Unix users) or 'grep for Windows' ( http://gnuwin32.sourceforge.net/packages/grep.htm ).
To find Call-ID you want, I would suggest you to capture network traffic with sniffer and view it in
Wireshark. Wireshark can also draw charts of SIP calls.


8. Troubleshooting
-------------------

1. If you encounter a problem when some service crashes because with the following error:
 
Exception in thread "UDPMessageProcessorThread"
java.lang.OutOfMemoryError: unable to create new native thread
       at java.lang.Thread.start0(Native Method)
       at java.lang.Thread.start(Thread.java:597)
       at gov.nist.javax.sip.stack.UDPMessageChannel.<init>(UDPMessageChannel.java:146)
       at gov.nist.javax.sip.stack.UDPMessageProcessor.run(UDPMessageProcessor.java:182)
       at java.lang.Thread.run(Thread.java:619)
       
this is usually means that the number of threads per process reached the limit.
Application tried to create a new thread but OS prohibited the attempt.

To fix this, you should play with the property gov.nist.javax.sip.THREAD_POOL_SIZE.
Set it to 50, 100, or 200. See description in conf/*.properties files.

P.S. In FreeBSD the max allowed number of threads per process can be viewed as:
     >sysctl kern.threads.max_threads_per_proc
     kern.threads.max_threads_per_proc: 1500

If nothing of above suits your case, you can report an issue.
To report an issue please visit http://code.google.com/p/openjsip/issues/list


9. Contacts
------------
       
Please send your comments, bugs, suggestions through Issues mechanism at project webpage 
( http://code.google.com/p/openjsip/issues/list ) or send them directly to ykrapiva@gmail.com.
