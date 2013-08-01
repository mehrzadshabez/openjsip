The current directory contains different tools that are necessary to collect statistics data of OpenJSIP services.
The current approach to collect data is to use command-line client ( %OPENJSIP_HOME%/bin/cmdclient ), put retreived
data to Round Robin database and render the results via RRDTool package.

Note: Windows scripts are not currently developed.

Directory layout
--------------------

  rrd/			- Contains Round Robin database files.
  scripts/		- Contains scripts to collect data and render the results to image files.
  png/			- Contains rendered images.
  index.html		- Example HTML page with statistics.


How to collect statistics
--------------------------

First of all, you should have RRDTool installed ( http://oss.oetiker.ch/rrdtool/ ).
This is a very powerfull tool for storing and rendering gathered statistics.

Then decide how often do you want to gather statistics ?
In scripts/ folder there are two type of scripts for gathering statistics - every minute or every 5 minutes.
If you want other period, you should customize the scripts manually.
For example, we choose period of 1 min.

Now we need to create .rrd (Round Robin database) files for holding collected data.
Example:

ykrapiva@extra/stats >pwd
/usr/home/ykrapiva/projects/java/openjsip/openjsip/extra/stats
ykrapiva@extra/stats >
ykrapiva@extra/stats >scripts/create-location-service-rrd-1min
ykrapiva@extra/stats >scripts/create-registrar-rrd-1min
ykrapiva@extra/stats >scripts/create-proxy-rrd-1min

Then we need to add scripts/update* and scripts/graph* scripts to crontab so they can be called every 1 min period.
Note: to edit crontab file, run "crontab -e".
My crontab example:

ykrapiva@stats/scripts >crontab -l
PATH=/etc:/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin

* * * * *	    cd /usr/home/ykrapiva/projects/java/openjsip/openjsip/extra/stats/scripts/; ( ./update-proxy-rrd-1min ; ./graph-proxy-rrd-1min ) > /dev/null 2>&1
* * * * *       cd /usr/home/ykrapiva/projects/java/openjsip/openjsip/extra/stats/scripts/; ( ./update-registrar-rrd-1min ; ./graph-registrar-rrd-1min ) > /dev/null 2>&1
* * * * *       cd /usr/home/ykrapiva/projects/java/openjsip/openjsip/extra/stats/scripts/; ( ./update-location-service-rrd-1min ; ./graph-location-service-rrd-1min ) > /dev/null 2>&1

The last, check manually scripts/png/ directory or open index.html file via browser.
The page will ( it should ;) ) contain all gathered statistics for the last 24 hours.
If not, remove "/dev/null 2>&1" string from crontab file and direct an output to some file to see what happens.
Note: Opera browser caches images, and doesn't reload them until the image opened in separate window. Strange...
