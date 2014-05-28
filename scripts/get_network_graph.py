#! /usr/bin/env python
# -*- Mode: python; py-indent-offset: 4; tab-width: 8; indent-tabs-mode: t; -*-

#
# Get Network Graph Information:
#  - Switches
#  - Links
#

import pprint
import os
import sys
import subprocess
import json
import collections
import argparse
import io
import time

from flask import Flask, json, Response, render_template, make_response, request

## Global Var ##
ControllerIP="127.0.0.1"
ControllerPort=8080

DEBUG=0
pp = pprint.PrettyPrinter(indent=4)

app = Flask(__name__)

## Worker Functions ##
def log_error(txt):
  print '%s' % (txt)

def debug(txt):
  if DEBUG:
    print '%s' % (txt)

# @app.route("/wm/onos/topology/links ")
# @app.route("/wm/onos/topology/switches ")
# Sample output:

def print_parsed_result(parsedResult):
  print '%s' % (parsedResult),

def get_network_switches():
  try:
    command = "curl -s \"http://%s:%s/wm/onos/topology/switches\"" % (ControllerIP, ControllerPort)
    debug("get_network_switches %s" % command)

    result = os.popen(command).read()
    debug("result %s" % result)
    if len(result) == 0:
      print "No Switches found"
      return;

    # parsedResult = result
    # parsedResult = json.loads(result)
    parsedResult = json.dumps(json.loads(result), indent=4)
    debug("parsed %s" % parsedResult)
  except:
    log_error("Controller IF has issue")
    exit(1)

  print_parsed_result(parsedResult)

def get_network_links():
  try:
    command = "curl -s \"http://%s:%s/wm/onos/topology/links\"" % (ControllerIP, ControllerPort)
    debug("get_network_links %s" % command)

    result = os.popen(command).read()
    debug("result %s" % result)
    if len(result) == 0:
      print "No Links found"
      return;

    # parsedResult = result
    # parsedResult = json.loads(result)
    parsedResult = json.dumps(json.loads(result), indent=4)
    debug("parsed %s" % parsedResult)
  except:
    log_error("Controller IF has issue")
    exit(1)

  print_parsed_result(parsedResult)


if __name__ == "__main__":
  usage_msg1 = "Usage:\n"
  usage_msg2 = "%s <arguments> : Print network information\n" % (sys.argv[0])
  usage_msg3 = "  Valid element names:\n"
  usage_msg4 = "    all              : Print all network elements\n"
  usage_msg5 = "    switches         : Print all switches and ports\n"
  usage_msg6 = "    links            : Print all links\n"
  usage_msg = usage_msg1 + usage_msg2 + usage_msg3 + usage_msg4 + usage_msg5
  usage_msg = usage_msg + usage_msg6

  # Usage info
  if len(sys.argv) > 1 and (sys.argv[1] == "-h" or sys.argv[1] == "--help"):
    print(usage_msg)
    exit(0)

  # Check arguments
  if len(sys.argv) < 2:
    log_error(usage_msg)
    exit(1)

  if (sys.argv[1] != "all" and sys.argv[1] != "switches" and sys.argv[1] != "links"):
    log_error(usage_msg)
    exit(1)

  # Do the work
  if (sys.argv[1] == "all" or sys.argv[1] == "switches"):
    get_network_switches()
  if (sys.argv[1] == "all" or sys.argv[1] == "links"):
    get_network_links()
