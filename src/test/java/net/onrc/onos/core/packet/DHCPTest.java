/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

/**
 * 
 */
package net.onrc.onos.core.packet;



import java.util.Arrays;
import java.util.ListIterator;

import junit.framework.TestCase;


/**
 * @author David Erickson (daviderickson@cs.stanford.edu)
 *
 */
public class DHCPTest extends TestCase {
    public byte[] dhcpPacket = new byte[] {
            (byte) 0x01, (byte) 0x01,
            (byte) 0x06, (byte) 0x00, (byte) 0x66, (byte) 0xf2, (byte) 0x8a,
            (byte) 0x11, (byte) 0x00, (byte) 0x06, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0xc0, (byte) 0x9f, (byte) 0x9f,
            (byte) 0xfe, (byte) 0xd8, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x63,
            (byte) 0x82, (byte) 0x53, (byte) 0x63, (byte) 0x35, (byte) 0x01,
            (byte) 0x01, (byte) 0x37, (byte) 0x0a, (byte) 0x01, (byte) 0x1c,
            (byte) 0x02, (byte) 0x03, (byte) 0x0f, (byte) 0x06, (byte) 0x0c,
            (byte) 0x28, (byte) 0x29, (byte) 0x2a, (byte) 0xff, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00
    };

    public byte[] dhcpPacket2 = new byte[] { (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00,
            (byte) 0xc0, (byte) 0x9f, (byte) 0x9e, (byte) 0x11, (byte) 0x84,
            (byte) 0x08, (byte) 0x00, (byte) 0x45, (byte) 0x00, (byte) 0x02,
            (byte) 0x40, (byte) 0x00, (byte) 0x04, (byte) 0x00, (byte) 0x00,
            (byte) 0x14, (byte) 0x11, (byte) 0xa4, (byte) 0xaa, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0x00, (byte) 0x44, (byte) 0x00,
            (byte) 0x43, (byte) 0x02, (byte) 0x2c, (byte) 0xdd, (byte) 0x9d,
            (byte) 0x01, (byte) 0x01, (byte) 0x06, (byte) 0x00, (byte) 0xa4,
            (byte) 0x9e, (byte) 0x11, (byte) 0x84, (byte) 0x00, (byte) 0x40,
            (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xc0,
            (byte) 0x9f, (byte) 0x9e, (byte) 0x11, (byte) 0x84, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x63, (byte) 0x82, (byte) 0x53, (byte) 0x63,
            (byte) 0x35, (byte) 0x01, (byte) 0x01, (byte) 0x37, (byte) 0x18,
            (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x05, (byte) 0x06,
            (byte) 0x0b, (byte) 0x0c, (byte) 0x0d, (byte) 0x0f, (byte) 0x10,
            (byte) 0x11, (byte) 0x12, (byte) 0x2b, (byte) 0x36, (byte) 0x3c,
            (byte) 0x43, (byte) 0x80, (byte) 0x81, (byte) 0x82, (byte) 0x83,
            (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87, (byte) 0x39,
            (byte) 0x02, (byte) 0x04, (byte) 0xec, (byte) 0x61, (byte) 0x11,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x5d, (byte) 0x02, (byte) 0x00,
            (byte) 0x00, (byte) 0x5e, (byte) 0x03, (byte) 0x01, (byte) 0x02,
            (byte) 0x01, (byte) 0x3c, (byte) 0x20, (byte) 0x50, (byte) 0x58,
            (byte) 0x45, (byte) 0x43, (byte) 0x6c, (byte) 0x69, (byte) 0x65,
            (byte) 0x6e, (byte) 0x74, (byte) 0x3a, (byte) 0x41, (byte) 0x72,
            (byte) 0x63, (byte) 0x68, (byte) 0x3a, (byte) 0x30, (byte) 0x30,
            (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0x3a, (byte) 0x55,
            (byte) 0x4e, (byte) 0x44, (byte) 0x49, (byte) 0x3a, (byte) 0x30,
            (byte) 0x30, (byte) 0x32, (byte) 0x30, (byte) 0x30, (byte) 0x31,
            (byte) 0xff, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00 };
    
    public byte[] dhcpPacket3 = new byte[] { 
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0x74, (byte) 0x44, (byte) 0x01, (byte) 0x72, 
            (byte) 0xd8, (byte) 0x41, (byte) 0x08, (byte) 0x00, (byte) 0x45, 
            (byte) 0x00, (byte) 0x01, (byte) 0x1f, (byte) 0x48, (byte) 0xcd, 
            (byte) 0x00, (byte) 0x00, (byte) 0x40, (byte) 0x11, (byte) 0x6f, 
            (byte) 0x6a, (byte) 0xc0, (byte) 0xa8, (byte) 0x00, (byte) 0xef, 
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00, 
            (byte) 0x44, (byte) 0x00, (byte) 0x43, (byte) 0x01, (byte) 0x0b, 
            (byte) 0xb3, (byte) 0x0f, (byte) 0x01, (byte) 0x01, (byte) 0x06, 
            (byte) 0x00, (byte) 0x82, (byte) 0x88, (byte) 0xa6, (byte) 0xc9, 
            (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x74, (byte) 0x44, (byte) 0x01, (byte) 0x72, (byte) 0xd8, 
            (byte) 0x41, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x63, (byte) 0x82, 
            (byte) 0x53, (byte) 0x63, (byte) 0x35, (byte) 0x01, (byte) 0x01, 
            (byte) 0x32, (byte) 0x04, (byte) 0xc0, (byte) 0xa8, (byte) 0x0a, 
            (byte) 0xa9, (byte) 0x39, (byte) 0x02, (byte) 0x02, (byte) 0x40, 
            (byte) 0x37, (byte) 0x03, (byte) 0x01, (byte) 0x03, (byte) 0x06, 
            (byte) 0xff                               
    };
    
    public byte[] dhcpPacketPXE = new byte[] { (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00,
            (byte) 0x19, (byte) 0xb9, (byte) 0xb0, (byte) 0x01, (byte) 0x44,
            (byte) 0x08, (byte) 0x00, (byte) 0x45, (byte) 0x10, (byte) 0x01,
            (byte) 0x48, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x80, (byte) 0x11, (byte) 0x2c, (byte) 0x98, (byte) 0x0a,
            (byte) 0x00, (byte) 0x02, (byte) 0xfe, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0x00, (byte) 0x43, (byte) 0x00,
            (byte) 0x44, (byte) 0x01, (byte) 0x34, (byte) 0xa6, (byte) 0xf0,
            (byte) 0x02, (byte) 0x01, (byte) 0x06, (byte) 0x00, (byte) 0xa0,
            (byte) 0x9e, (byte) 0x0c, (byte) 0x13, (byte) 0x00, (byte) 0x04,
            (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x0a, (byte) 0x00, (byte) 0x02, (byte) 0x14,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xc0,
            (byte) 0x9f, (byte) 0x9e, (byte) 0x0c, (byte) 0x13, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x78, (byte) 0x65,
            (byte) 0x6e, (byte) 0x73, (byte) 0x65, (byte) 0x72, (byte) 0x76,
            (byte) 0x65, (byte) 0x72, (byte) 0x5f, (byte) 0x35, (byte) 0x2e,
            (byte) 0x36, (byte) 0x2f, (byte) 0x70, (byte) 0x78, (byte) 0x65,
            (byte) 0x6c, (byte) 0x69, (byte) 0x6e, (byte) 0x75, (byte) 0x78,
            (byte) 0x2e, (byte) 0x30, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x63, (byte) 0x82, (byte) 0x53, (byte) 0x63,
            (byte) 0x35, (byte) 0x01, (byte) 0x02, (byte) 0x36, (byte) 0x04,
            (byte) 0x0a, (byte) 0x00, (byte) 0x02, (byte) 0xfe, (byte) 0x33,
            (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0xa8, (byte) 0xc0,
            (byte) 0x01, (byte) 0x04, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0x00, (byte) 0x03, (byte) 0x04, (byte) 0x0a, (byte) 0x00,
            (byte) 0x02, (byte) 0xfe, (byte) 0x0c, (byte) 0x0d, (byte) 0x64,
            (byte) 0x6e, (byte) 0x72, (byte) 0x63, (byte) 0x2d, (byte) 0x68,
            (byte) 0x6f, (byte) 0x73, (byte) 0x74, (byte) 0x30, (byte) 0x30,
            (byte) 0x32, (byte) 0x30, (byte) 0xff, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };

    public byte[] dhcpPacketBadOption1 = new byte[] { (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00,
            (byte) 0x19, (byte) 0xb9, (byte) 0xb0, (byte) 0x01, (byte) 0x44,
            (byte) 0x08, (byte) 0x00, (byte) 0x45, (byte) 0x10, (byte) 0x01,
            (byte) 0x48, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x80, (byte) 0x11, (byte) 0x2c, (byte) 0x98, (byte) 0x0a,
            (byte) 0x00, (byte) 0x02, (byte) 0xfe, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0x00, (byte) 0x44, (byte) 0x00,
            (byte) 0x43, (byte) 0x01, (byte) 0x34, (byte) 0xa6, (byte) 0xf0,
            (byte) 0x02, (byte) 0x01, (byte) 0x06, (byte) 0x00, (byte) 0xa0,
            (byte) 0x9e, (byte) 0x0c, (byte) 0x13, (byte) 0x00, (byte) 0x04,
            (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x0a, (byte) 0x00, (byte) 0x02, (byte) 0x14,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xc0,
            (byte) 0x9f, (byte) 0x9e, (byte) 0x0c, (byte) 0x13, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x78, (byte) 0x65,
            (byte) 0x6e, (byte) 0x73, (byte) 0x65, (byte) 0x72, (byte) 0x76,
            (byte) 0x65, (byte) 0x72, (byte) 0x5f, (byte) 0x35, (byte) 0x2e,
            (byte) 0x36, (byte) 0x2f, (byte) 0x70, (byte) 0x78, (byte) 0x65,
            (byte) 0x6c, (byte) 0x69, (byte) 0x6e, (byte) 0x75, (byte) 0x78,
            (byte) 0x2e, (byte) 0x30, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x63, (byte) 0x82, (byte) 0x53, (byte) 0x63,
            (byte) 0x35, (byte) 0x01, (byte) 0x01, (byte) 0x36, (byte) 0x04,
            (byte) 0x0a, (byte) 0x00, (byte) 0x02, (byte) 0xfe, (byte) 0x33,
            (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0xa8, (byte) 0xc0,
            (byte) 0x01, (byte) 0x04, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0x00, (byte) 0x03, (byte) 0x04, (byte) 0x0a, (byte) 0x00,
            (byte) 0x02, (byte) 0xfe, (byte) 0x0c, (byte) 0x30, (byte) 0x64,
            (byte) 0x6e, (byte) 0x72, (byte) 0x63, (byte) 0x2d, (byte) 0x68,
            (byte) 0x6f, (byte) 0x73, (byte) 0x74, (byte) 0x30, (byte) 0x30,
            (byte) 0x32, (byte) 0x30, (byte) 0xff, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };
    
    public byte[] dhcpPacketBadHeader = new byte[] { (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00,
            (byte) 0x19, (byte) 0xb9, (byte) 0xb0, (byte) 0x01, (byte) 0x44,
            (byte) 0x08, (byte) 0x00, (byte) 0x45, (byte) 0x10, (byte) 0x01,
            (byte) 0x48, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x80, (byte) 0x11, (byte) 0x2c, (byte) 0x98, (byte) 0x0a,
            (byte) 0x00, (byte) 0x02, (byte) 0xfe, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0x00, (byte) 0x44, (byte) 0x00,
            (byte) 0x43, (byte) 0x01, (byte) 0x34, (byte) 0xa6, (byte) 0xf0,
            (byte) 0x02, (byte) 0x01, (byte) 0x06, (byte) 0x00, (byte) 0xa0,
            (byte) 0x9e, (byte) 0x0c, (byte) 0x13, (byte) 0x00, (byte) 0x04,
            (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x0a, (byte) 0x00, (byte) 0x02, (byte) 0x14,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xc0,
            (byte) 0x9f, (byte) 0x9e, (byte) 0x0c, (byte) 0x13, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x78, (byte) 0x65,
            (byte) 0x6e, (byte) 0x73, (byte) 0x65, (byte) 0x72, (byte) 0x76,
            (byte) 0x65, (byte) 0x72, (byte) 0x5f, (byte) 0x35, (byte) 0x2e,
            (byte) 0x36, (byte) 0x2f, (byte) 0x70, (byte) 0x78, (byte) 0x65,
            (byte) 0x6c, (byte) 0x69, (byte) 0x6e, (byte) 0x75, (byte) 0x78,
            (byte) 0x2e, (byte) 0x30, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };
    
    private void resetChecksumsAndLengths(IPv4 ipv4, UDP udp) {
        ipv4.setChecksum((short)0);
        udp.setChecksum((short)0);
    }
    
    public void testSerialize() {
        DHCP dhcp = new DHCP();
        dhcp.deserialize(dhcpPacket, 0, dhcpPacket.length);
        byte[] result = dhcp.serialize();
        assertTrue(Arrays.equals(this.dhcpPacket, result));
    }

    public void testDeSerialize() {
        Ethernet eth = new Ethernet();
        eth.deserialize(dhcpPacket2, 0, dhcpPacket2.length);
        assertTrue(eth.getPayload() instanceof IPv4);
        IPv4 ipv4 = (IPv4) eth.getPayload();
        assertTrue(ipv4.getPayload() instanceof UDP);
        UDP udp = (UDP) ipv4.getPayload();
        assertTrue(udp.getPayload() instanceof DHCP);
        DHCP dhcp = (DHCP) udp.getPayload();
        /** The invalid option in DHCP packet is dropped. Reset checksums and
         *  length field so that the serialize() function can re-compute them
         */
        resetChecksumsAndLengths(ipv4, udp);
        assertEquals(DHCP.OPCODE_REQUEST, dhcp.getOpCode());
    }
    
    public void testDeSerializeReSerialize() {
        Ethernet eth = new Ethernet();
        eth.deserialize(dhcpPacket3, 0, dhcpPacket3.length);
        assertTrue(eth.getPayload() instanceof IPv4);
        IPv4 ipv4 = (IPv4) eth.getPayload();
        assertTrue(ipv4.getPayload() instanceof UDP);
        
        byte[] serializedPacket = eth.serialize();
        Ethernet eth2 = new Ethernet();
        eth2.deserialize(serializedPacket, 0, serializedPacket.length);
        IPv4 ipv42 = (IPv4) eth2.getPayload();

        short ipchecksum = ipv42.getChecksum();
        ipv42.setChecksum((short) 0);
        eth2.serialize();        
        assertEquals(ipchecksum, ipv42.getChecksum());
    }

    public void testDeSerializePXE() {
        Ethernet eth = new Ethernet();
        eth.deserialize(dhcpPacketPXE, 0, dhcpPacketPXE.length);
        assertTrue(eth.getPayload() instanceof IPv4);
        IPv4 ipv4 = (IPv4) eth.getPayload();
        assertTrue(ipv4.getPayload() instanceof UDP);
        UDP udp = (UDP) ipv4.getPayload();
        assertTrue(udp.getPayload() instanceof DHCP);
        DHCP dhcp = (DHCP) udp.getPayload();
        /** The invalid option in DHCP packet is dropped. Reset checksums and
         *  length field so that the serialize() function can re-compute them
         */
        resetChecksumsAndLengths(ipv4, udp);
        
        assertEquals(DHCP.OPCODE_REPLY, dhcp.getOpCode());
        assertEquals("xenserver_5.6/pxelinux.0", dhcp.getBootFileName());

        byte[] result = eth.serialize();
        assertTrue(Arrays.equals(this.dhcpPacketPXE, result));
    }
    
    public void testDeSerializeBad1() {
        Ethernet eth = new Ethernet();
        eth.deserialize(dhcpPacketBadOption1, 0, dhcpPacketBadOption1.length);
        assertTrue(eth.getPayload() instanceof IPv4);
        IPv4 ipv4 = (IPv4) eth.getPayload();
        assertTrue(ipv4.getPayload() instanceof UDP);
        UDP udp = (UDP) ipv4.getPayload();
        assertTrue(udp.getPayload() instanceof DHCP);
        DHCP dhcp = (DHCP) udp.getPayload();
        /** The invalid option in DHCP packet is dropped. Reset checksums and
         *  length field so that the serialize() function can re-compute them
         */
        resetChecksumsAndLengths(ipv4, udp);
        
        assertEquals(DHCP.OPCODE_REPLY, dhcp.getOpCode());
        ListIterator<DHCPOption> lit = dhcp.getOptions().listIterator();
        // Expect 5 correct options and an END option.
        assertEquals(dhcp.getOptions().size(), 6);
        while (lit.hasNext()) {
            DHCPOption option = lit.next();
            assertFalse(option.code == (byte)0x0c);
        }

        byte[] result = eth.serialize();
        // Since one option is badly formated, the result is different.
        assertFalse(Arrays.equals(this.dhcpPacketPXE, result));
    }
    
    public void testDeSerializeBadHeader() {
        Ethernet eth = new Ethernet();
        eth.deserialize(dhcpPacketBadHeader, 0, dhcpPacketBadHeader.length);
        assertTrue(eth.getPayload() instanceof IPv4);
        IPv4 ipv4 = (IPv4) eth.getPayload();
        assertTrue(ipv4.getPayload() instanceof UDP);
        UDP udp = (UDP) ipv4.getPayload();
        assertTrue(udp.getPayload() instanceof DHCP);
        DHCP dhcp = (DHCP) udp.getPayload();

        assertEquals(UDP.DHCP_CLIENT_PORT, udp.getSourcePort());
        assertEquals(UDP.DHCP_SERVER_PORT, udp.getDestinationPort());
        
        // should get invalid opCode of 0
        assertEquals(0, dhcp.getOpCode());
    }
}
