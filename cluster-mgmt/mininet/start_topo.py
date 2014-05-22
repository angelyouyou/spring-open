#!/usr/bin/env python

"""
start_topo.py : Mininet topology builder for multiple controller.

This script reads config file and creates a Mininet topology.

Usage:

    $ sudo -E start_topo.py [filename]
    
    filename: Config filename to specify topology.
              If omitted, "topo.`hostname`.py" or "topo.py" in current
              directory will be used.
"""

import platform
import time
import os.path
import getpass
from sys import argv
from time import sleep

from mininet.node import Controller, OVSSwitch, RemoteController
from mininet.net import Mininet
from mininet.cli import CLI
from mininet.topo import Topo
from mininet.log import setLogLevel, info, warn

usage = """
Usage: Create Mininet topology.
  $ sudo -E %s [filename]
      filename: config file (topo.`hostname`.py by default)
""" % os.path.basename( argv[0] )

# Mininet must be run by root user
if ( getpass.getuser() != 'root' ):
    print usage
    exit( 0 )

argc = len( argv )
if ( argc > 2 ):
    print usage
    exit( 0 )

if ( argc == 2 and ( argv[ 1 ] == "-h" or argv[ 1 ] == "--help" ) ):
    print usage
    exit( 0 )

if ( argc == 2 ):
    topofile = argv[1]
else: 
    hostname = platform.node()
    default_topo = 'topo.%s.py' % hostname
    fallback_topo = 'topo.py'
    topofile = default_topo if os.path.exists(default_topo) else fallback_topo

if not os.path.exists( topofile ):
    print( 'Config file %s not found' % default_topo )
    exit( 1 )

execfile( topofile )
conf = createTopo()
print conf

class ConfigTopo( Topo ):
    "Topology created from config."
  
    def __init__( self, *args, **kwargs ):
        "Create custom topo."
  
        Topo.__init__( self, *args, **kwargs )
        
        # Add hosts and switches
        nmap = {}
        for s, prop in conf[ 'switches' ].iteritems():
            # make sure encoding is ASCII (if not, virtual eth creation fails)
            s = s.strip().encode( 'ascii' )
            dpid = prop[ 'dpid' ].strip().encode( 'ascii' )
            nmap[ s ] = self.addSwitch( s, dpid=dpid )
        
        for h, prop in conf[ 'hosts' ].iteritems():
            h = h.strip().encode( 'ascii' )
            nmap[ h ] =self.addHost( h )
        
        # Add links
        for l in conf[ 'links' ]:
            node1 = nmap[ l[ 'node1' ] ].strip().encode( 'ascii' )
            node2 = nmap[ l[ 'node2' ] ].strip().encode( 'ascii' )
            self.addLink( node1, node2 )

class ClusterConnectedSwitch( OVSSwitch ):
    "OVSSwitch connected to controller cluster."
  
    def start( self, controllers ):
        # make sure controllers contains only a ControllerCluster
        assert len( controllers ) == 1
        ccluster = controllers[ 0 ]
        assert type( ccluster ) == ControllerCluster
        
        controller_list = ccluster.clist( self.name )
        # TODO: manage order of controllers to control mastership
        OVSSwitch.start( self, controllers=controller_list )

class ControllerCluster( Controller ):
    "Cluster of controllers. Relationship between controllers and switches is defined by config file"
    
    def __init__( self, name, **params ):
        self.sw_clist_map = {}
        self.cmap = {}
        
        Controller.__init__( self, name, **params )
        
        for cname, addr in conf[ 'controllers' ].iteritems():
            cname = cname.strip().encode( 'ascii' )
            addr = addr.strip().encode( 'ascii' )
            ip, port = addr.split( ':' )
            self.cmap[ cname ] = RemoteController( cname, ip=ip, port=int( port ) )
        
        for sw, params in conf[ 'switches' ].iteritems():
            clist = []
            for c in params[ 'controllers' ]:
                assert not self.cmap[ c ] is None
                clist.append( self.cmap[ c ] )
            self.sw_clist_map[ sw ] = clist
    
    def start( self ):
        # do nothing
        return
    
    def stop( self ):
        # do nothing
        return
    
    def checkListening( self ):
        for c in self.cmap.values():
            c.checkListening()
    
    def clist( self, sw ):
        return self.sw_clist_map[ sw ]

if __name__ == '__main__':
    setLogLevel( 'info' )
    net = Mininet( topo=ConfigTopo(),
                   controller=ControllerCluster,
                   switch=ClusterConnectedSwitch )
    net.start()
    CLI( net )
    net.stop()