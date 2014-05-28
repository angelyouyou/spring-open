#! /usr/bin/env python
import pprint
import os
import sys
import subprocess
import json
import argparse
import io
import time
import random
import re
from urllib2 import Request, urlopen, URLError, HTTPError

from flask import Flask, json, Response, render_template, make_response, request

# paths for local access
ONOS_DIR = os.getenv("HOME") + "/ONOS"
ONOS_SCRIPTS_DIR = ONOS_DIR + "/scripts"
ONS2014_DEMO_SCRIPTS_DIR = ONOS_DIR + "/sample/ONS2014demo/scripts"
ONS2014_DEMO_WEB_DIR = ONOS_DIR + "/sample/ONS2014demo/web"
LINK_FILE = ONS2014_DEMO_SCRIPTS_DIR + "/link.json"
CONFIG_FILE = ONS2014_DEMO_SCRIPTS_DIR + "/config.json"

# paths for remote access
REMOTE_DEMO_SCRIPTS_DIR = "ONOS/sample/ONS2014demo/scripts"

## Global Var for this proxy script setting.
# "0.0.0.0" means any interface
ProxyIP="0.0.0.0"
ProxyPort=9000

## Global Var for ON.Lab local REST ##
RestIP="localhost"
RestPort=8080
ONOS_DEFAULT_HOST="localhost" ;# Has to set if LB=False
DEBUG=1

pp = pprint.PrettyPrinter(indent=4)
app = Flask(__name__)

def read_config():
  global LB, TESTBED, controllers, core_switches, ONOS_GUI3_HOST, ONOS_GUI3_CONTROL_HOST
  f = open(CONFIG_FILE)
  conf = json.load(f)
  LB = conf['LB']
  TESTBED = conf['TESTBED']
  controllers = conf['controllers']
  core_switches=conf['core_switches']
  ONOS_GUI3_HOST=conf['ONOS_GUI3_HOST']
  ONOS_GUI3_CONTROL_HOST=conf['ONOS_GUI3_CONTROL_HOST']
  f.close()

def read_link_def():
  global link_def
  f=open(LINK_FILE)
  try:
    link_def=json.load(f)
    f.close()
  except:
    print "Can't read link def file (link.json)"
    sys.exit(1)

def get_link_ports(src_dpid, dst_dpid):
  ret = (-1, -1)
  for link in link_def:
    if link['src-switch'] == src_dpid and link['dst-switch'] == dst_dpid:
        ret = (link['src-port'], link['dst-port'])
        break
  return ret

## Worker Functions ##
def log_error(txt):
  print '%s' % (txt)

def debug(txt):
  if DEBUG:
    print '%s' % (txt)

### File Fetch ###
@app.route('/', methods=['GET'])
@app.route('/<filename>', methods=['GET'])
@app.route('/js/<filename>', methods=['GET'])
@app.route('/d3/<filename>', methods=['GET'])
@app.route('/css/<filename>', methods=['GET'])
@app.route('/assets/<filename>', methods=['GET'])
@app.route('/data/<filename>', methods=['GET'])
def return_file(filename="index.html"):
  if request.path == "/":
    fullpath = ONS2014_DEMO_WEB_DIR + "/index.html"
  else:
    fullpath = ONS2014_DEMO_WEB_DIR + "/" + str(request.path)[1:]

  try:
    open(fullpath)
  except:
    response = make_response("Cannot find a file: %s" % (fullpath), 500)
    response.headers["Content-type"] = "text/html"
    return response

  response = make_response(open(fullpath).read())
  suffix = fullpath.split(".")[-1]

  if suffix == "html" or suffix == "htm":
    response.headers["Content-type"] = "text/html"
  elif suffix == "js":
    response.headers["Content-type"] = "application/javascript"
  elif suffix == "css":
    response.headers["Content-type"] = "text/css"
  elif suffix == "png":
    response.headers["Content-type"] = "image/png"
  elif suffix == "svg":
    response.headers["Content-type"] = "image/svg+xml"

  return response

## Proxy ##
@app.route("/proxy/gui/link/<cmd>/<src_dpid>/<src_port>/<dst_dpid>/<dst_port>")
def proxy_link_change(cmd, src_dpid, src_port, dst_dpid, dst_port):
  url = "%s/gui/link/%s/%s/%s/%s/%s" % (ONOS_GUI3_CONTROL_HOST, cmd, src_dpid, src_port, dst_dpid, dst_port)
  try:
    response = urlopen(url)
    result = response.read()
  except:
    result = ""
    print "REST IF has issue %s" % url

  resp = Response(result, status=200, mimetype='application/json')
  return resp

@app.route("/proxy/gui/switchctrl/<cmd>")
def proxy_switch_controller_setting(cmd):
  url = "%s/gui/switchctrl/%s" % (ONOS_GUI3_CONTROL_HOST, cmd)
  try:
    response = urlopen(url)
    result = response.read()
  except:
    result = ""
    print "REST IF has issue %s" % url

  resp = Response(result, status=200, mimetype='application/json')
  return resp

@app.route("/proxy/gui/switch/<cmd>/<dpid>")
def proxy_switch_status_change(cmd, dpid):
  url = "%s/gui/switch/%s/%s" % (ONOS_GUI3_CONTROL_HOST, cmd, dpid)
  try:
    response = urlopen(url)
    result = response.read()
  except:
    result = ""
    print "REST IF has issue %s" % url

  resp = Response(result, status=200, mimetype='application/json')
  return resp

@app.route("/proxy/gui/controller/<cmd>/<controller_name>")
def proxy_controller_status_change(cmd, controller_name):
  url = "%s/gui/controller/%s/%s" % (ONOS_GUI3_CONTROL_HOST, cmd, controller_name)
  try:
    response = urlopen(url)
    result = response.read()
  except:
    result = ""
    print "REST IF has issue %s" % url
 
  resp = Response(result, status=200, mimetype='application/json')
  return resp

@app.route("/proxy/gui/addflow/<src_dpid>/<src_port>/<dst_dpid>/<dst_port>/<srcMAC>/<dstMAC>")
def proxy_add_flow(src_dpid, src_port, dst_dpid, dst_port, srcMAC, dstMAC):
  try:
    url = "%s/gui/addflow/%s/%s/%s/%s/%s/%s" % (ONOS_GUI3_CONTROL_HOST, src_dpid, src_port, dst_dpid, dst_port, srcMAC, dstMAC)
    #print "proxy gui addflow " + url
    (code, result) = get_json(url)
  except:
    print "REST IF has issue %s" % url
    print "Result %s" % result
    exit()

  resp = Response(result, status=200, mimetype='application/json')
  return resp

@app.route("/proxy/gui/delflow/<flow_id>")
def proxy_del_flow(flow_id):
  url = "%s/gui/delflow/%s" % (ONOS_GUI3_CONTROL_HOST, flow_id)
  try:
    response = urlopen(url)
    result = response.read()
  except:
    result = ""
    print "REST IF has issue %s" % url

  resp = Response(result, status=200, mimetype='application/json')
  return resp

@app.route("/proxy/gui/iperf/start/<flow_id>/<duration>/<samples>")
def proxy_iperf_start(flow_id,duration,samples):
  url = "%s/gui/iperf/start/%s/%s/%s" % (ONOS_GUI3_CONTROL_HOST, flow_id, duration, samples)
  try:
    response = urlopen(url)
    result = response.read()
  except:
    result = ""
    print "REST IF has issue %s" % url

  resp = Response(result, status=200, mimetype='application/json')
  return resp

@app.route("/proxy/gui/iperf/rate/<flow_id>")
def proxy_iperf_rate(flow_id):
  url = "%s/gui/iperf/rate/%s" % (ONOS_GUI3_CONTROL_HOST, flow_id)
  try:
    response = urlopen(url)
    result = response.read()
  except:
    result = ""
    print "REST IF has issue %s" % url

  resp = Response(result, status=200, mimetype='application/json')
  return resp

@app.route("/proxy/gui/reset")
def proxy_gui_reset():
  url = "%s/gui/reset" % (ONOS_GUI3_CONTROL_HOST)
  try:
    response = urlopen(url)
    result = response.read()
  except:
    result = ""
    print "REST IF has issue %s" % url

  resp = Response(result, status=200, mimetype='application/json')
  return resp

@app.route("/proxy/gui/scale")
def proxy_gui_scale():
  url = "%s/gui/scale" % (ONOS_GUI3_CONTROL_HOST)
  try:
    response = urlopen(url)
    result = response.read()
  except:
    result = ""
    print "REST IF has issue %s" % url

  resp = Response(result, status=200, mimetype='application/json')
  return resp

###### ONOS REST API ##############################
## Worker Func ###
def get_json(url):
  code = 200;
  try:
    response = urlopen(url)
  except URLError, e:
    print "get_json: REST IF %s has issue. Reason: %s" % (url, e.reason)
    result = ""
    return (500, result)
  except HTTPError, e:
    print "get_json: REST IF %s has issue. Code %s" % (url, e.code)
    result = ""
    return (e.code, result)

  result = response.read()
#  parsedResult = json.loads(result)
  return (code, result)

def pick_host():
  if LB == True:
    nr_host=len(controllers)
    r=random.randint(0, nr_host - 1)
    host=controllers[r]
  else:
    host=ONOS_DEFAULT_HOST

  return "http://" + host + ":8080"

## Switch ##
@app.route("/wm/onos/topology/switches")
def switches():
  if request.args.get('proxy') == None:
    host = pick_host()
  else:
    host = ONOS_GUI3_HOST

  url ="%s/wm/onos/topology/switches" % (host)
  (code, result) = get_json(url)

  resp = Response(result, status=code, mimetype='application/json')
  return resp

## Link ##
@app.route("/wm/onos/topology/links")
def links():
  if request.args.get('proxy') == None:
    host = pick_host()
  else:
    host = ONOS_GUI3_HOST

  url ="%s/wm/onos/topology/links" % (host)
  (code, result) = get_json(url)

  resp = Response(result, status=code, mimetype='application/json')
  return resp

## FlowSummary ##
@app.route("/wm/onos/flows/getsummary/<start>/<range>/json")
def flows(start, range):
  if request.args.get('proxy') == None:
    host = pick_host()
  else:
    host = ONOS_GUI3_HOST

  url ="%s/wm/onos/flows/getsummary/%s/%s/json" % (host, start, range)
  (code, result) = get_json(url)

  resp = Response(result, status=code, mimetype='application/json')
  return resp

@app.route("/wm/onos/registry/controllers/json")
def registry_controllers():
  if request.args.get('proxy') == None:
    host = pick_host()
  else:
    host = ONOS_GUI3_HOST

  url= "%s/wm/onos/registry/controllers/json" % (host)
  (code, result) = get_json(url)

  resp = Response(result, status=code, mimetype='application/json')
  return resp


@app.route("/wm/onos/registry/switches/json")
def registry_switches():
  if request.args.get('proxy') == None:
    host = pick_host()
  else:
    host = ONOS_GUI3_HOST

  url="%s/wm/onos/registry/switches/json" % (host)
  (code, result) = get_json(url)

  resp = Response(result, status=code, mimetype='application/json')
  return resp

def node_id(switch_array, dpid):
  id = -1
  for i, val in enumerate(switch_array):
    if val['name'] == dpid:
      id = i
      break

  return id

## API for ON.Lab local GUI ##
@app.route('/topology', methods=['GET'])
def topology_for_gui():
  try:
    url="http://%s:%s/wm/onos/topology/switches" % (RestIP, RestPort)
    (code, result) = get_json(url)
    parsedResult = json.loads(result)
  except:
    log_error("REST IF has issue: %s" % url)
    log_error("%s" % result)
    return

  topo = {}
  switches = []
  links = []
  devices = []

  for v in parsedResult:
    if v.has_key('dpid'):
#      if v.has_key('dpid') and str(v['state']) == "ACTIVE":#;if you want only ACTIVE nodes
      dpid = str(v['dpid'])
      state = str(v['state'])
      sw = {}
      sw['name']=dpid
      sw['group']= -1

      if state == "INACTIVE":
        sw['group']=0
      switches.append(sw)

  try:
    url="http://%s:%s/wm/onos/registry/switches/json" % (RestIP, RestPort)
    (code, result) = get_json(url)
    parsedResult = json.loads(result)
  except:
    log_error("REST IF has issue: %s" % url)
    log_error("%s" % result)

  for key in parsedResult:
    dpid = key
    ctrl = parsedResult[dpid][0]['controllerId']
    sw_id = node_id(switches, dpid)
    if sw_id != -1:
      if switches[sw_id]['group'] != 0:
        switches[sw_id]['group'] = controllers.index(ctrl) + 1

  try:
    url = "http://%s:%s/wm/onos/topology/links" % (RestIP, RestPort)
    (code, result) = get_json(url)
    parsedResult = json.loads(result)
  except:
    log_error("REST IF has issue: %s" % url)
    log_error("%s" % result)
    return
#    sys.exit(0)

  for v in parsedResult:
    link = {}
    if v.has_key('dst-switch'):
      dst_dpid = str(v['dst-switch'])
      dst_id = node_id(switches, dst_dpid)
    if v.has_key('src-switch'):
      src_dpid = str(v['src-switch'])
      src_id = node_id(switches, src_dpid)
    link['source'] = src_id
    link['target'] = dst_id

    #onpath = 0
    #for (s,d) in path:
    #  if s == v['src-switch'] and d == v['dst-switch']:
    #    onpath = 1
    #    break
    #link['type'] = onpath

    links.append(link)

  topo['nodes'] = switches
  topo['links'] = links

  js = json.dumps(topo)
  resp = Response(js, status=200, mimetype='application/json')
  return resp

@app.route("/wm/floodlight/topology/toporoute/<v1>/<p1>/<v2>/<p2>/json")
def shortest_path(v1, p1, v2, p2):
  try:
    url = "http://%s:%s/wm/onos/topology/switches" % (RestIP, RestPort)
    (code, result) = get_json(url)
    parsedResult = json.loads(result)
  except:
    log_error("REST IF has issue: %s" % command)
    log_error("%s" % result)
    return

  topo = {}
  switches = []
  links = []

  for v in parsedResult:
    if v.has_key('dpid'):
      dpid = str(v['dpid'])
      state = str(v['state'])
      sw = {}
      sw['name']=dpid
      if str(v['state']) == "ACTIVE":
        if dpid[-2:-1] == "a":
         sw['group']=1
        if dpid[-2:-1] == "b":
         sw['group']=2
        if dpid[-2:-1] == "c":
         sw['group']=3
      if str(v['state']) == "INACTIVE":
         sw['group']=0

      switches.append(sw)

  try:
    url = "http://%s:%s/wm/onos/topology/route/%s/%s/%s/%s/json" % (RestIP, RestPort, v1, p1, v2, p2)
    (code, result) = get_json(url)
    parsedResult = json.loads(result)
  except:
    log_error("No route")
    parsedResult = []

  path = [];
  for i, v in enumerate(parsedResult):
    if i < len(parsedResult) - 1:
      sdpid= parsedResult['flowEntries'][i]['dpid']['value']
      ddpid= parsedResult['flowEntries'][i+1]['dpid']['value']
      path.append( (sdpid, ddpid))

  try:
    url = "http://%s:%s/wm/onos/topology/links" % (RestIP, RestPort)
    (code, result) = get_json(url)
    parsedResult = json.loads(result)
  except:
    log_error("REST IF has issue: %s" % command)
    log_error("%s" % result)
    return

  for v in parsedResult:
    link = {}
    if v.has_key('dst-switch'):
      dst_dpid = str(v['dst-switch'])
      dst_id = node_id(switches, dst_dpid)
    if v.has_key('src-switch'):
      src_dpid = str(v['src-switch'])
      src_id = node_id(switches, src_dpid)
    link['source'] = src_id
    link['target'] = dst_id
    onpath = 0
    for (s,d) in path:
      if s == v['src-switch'] and d == v['dst-switch']:
        onpath = 1
        break

    link['type'] = onpath
    links.append(link)

  topo['nodes'] = switches
  topo['links'] = links

  js = json.dumps(topo)
  resp = Response(js, status=200, mimetype='application/json')
  return resp

@app.route("/wm/floodlight/core/controller/switches/json")
def query_switch():
  try:
    url = "http://%s:%s/wm/onos/topology/switches" % (RestIP, RestPort)
    (code, result) = get_json(url)
    parsedResult = json.loads(result)
  except:
    log_error("REST IF has issue: %s" % url)
    log_error("%s" % result)
    return
#    sys.exit(0)

#  print command
#  print result
  switches_ = []
  for v in parsedResult:
    if v.has_key('dpid'):
      if v.has_key('dpid') and str(v['state']) == "ACTIVE":#;if you want only ACTIVE nodes
        dpid = str(v['dpid'])
        state = str(v['state'])
        sw = {}
        sw['dpid']=dpid
        sw['active']=state
        switches_.append(sw)

#  pp.pprint(switches_)
  js = json.dumps(switches_)
  resp = Response(js, status=200, mimetype='application/json')
  return resp

## return fake stat for now
@app.route("/wm/floodlight/core/switch/<switchId>/<statType>/json")
def switch_stat(switchId, statType):
    if statType == "desc":
        desc=[{"length":1056,"serialNumber":"None","manufacturerDescription":"Nicira Networks, Inc.","hardwareDescription":"Open vSwitch","softwareDescription":"1.4.0+build0","datapathDescription":"None"}]
        ret = {}
        ret[switchId]=desc
    elif statType == "aggregate":
        aggr = {"packetCount":0,"byteCount":0,"flowCount":0}
        ret = {}
        ret[switchId]=aggr
    else:
        ret = {}

    js = json.dumps(ret)
    resp = Response(js, status=200, mimetype='application/json')
    return resp

@app.route("/controller_status")
def controller_status():
  url= "http://%s:%d/wm/onos/registry/controllers/json" % (RestIP, RestPort)
  (code, result) = get_json(url)
  parsedResult = json.loads(result)

  cont_status=[]
  for i in controllers:
    status={}
    if i in parsedResult:
      onos=1
    else:
      onos=0
    status["name"]=i
    status["onos"]=onos
    status["cassandra"]=0
    cont_status.append(status)

  js = json.dumps(cont_status)
  resp = Response(js, status=200, mimetype='application/json')
  return resp


### Command ###
@app.route("/gui/controller/<cmd>/<controller_name>")
def controller_status_change(cmd, controller_name):
  if (TESTBED == "hw"):
    start_onos="/home/admin/bin/onos start %s" % (controller_name[-1:])
#    start_onos="/home/admin/bin/onos start %s > /tmp/debug " % (controller_name[-1:])
    stop_onos="/home/admin/bin/onos stop %s" % (controller_name[-1:])
#    stop_onos="/home/admin/bin/onos stop %s > /tmp/debug " % (controller_name[-1:])
#    print "Debug: Controller command %s called %s" % (cmd, controller_name)
  else:
    # No longer use -i to specify keys (use .ssh/config to specify it)
    start_onos="ssh %s \"cd ONOS; ./onos.sh core start\"" % (controller_name)
    stop_onos="ssh %s \"cd ONOS; ./onos.sh core stop\"" % (controller_name)
#    start_onos="ssh -i ~/.ssh/onlabkey.pem %s ONOS/start-onos.sh start" % (controller_name)
#    stop_onos="ssh -i ~/.ssh/onlabkey.pem %s ONOS/start-onos.sh stop" % (controller_name)

  if cmd == "up":
    result=os.popen(start_onos).read()
    ret = "controller %s is up: %s" % (controller_name, result)
  elif cmd == "down":
    result=os.popen(stop_onos).read()
    ret = "controller %s is down: %s" % (controller_name, result)

  return ret

@app.route("/gui/switchctrl/<cmd>")
def switch_controller_setting(cmd):
  if cmd =="local":
    print "All aggr switches connects to local controller only"
    result=""
    if (TESTBED == "sw"):
      for i in range(1, len(controllers)):
          cmd_string="ssh %s 'cd %s; ./ctrl-local.sh'" % (controllers[i], REMOTE_DEMO_SCRIPTS_DIR)
          result += os.popen(cmd_string).read()
    else:
      cmd_string="cd; switch local > /tmp/watch"
      result += os.popen(cmd_string).read()
  elif cmd =="all":
    print "All aggr switches connects to all controllers except for core controller"
    result=""
    if (TESTBED == "sw"):
      for i in range(1, len(controllers)):
        cmd_string="ssh %s 'cd %s; ./ctrl-add-ext.sh'" % (controllers[i], REMOTE_DEMO_SCRIPTS_DIR)
#        cmd_string="ssh -i ~/.ssh/onlabkey.pem %s 'cd %s; ./ctrl-add-ext.sh'" % (controllers[i], REMOTE_DEMO_SCRIPTS_DIR)
        print "cmd is: "+cmd_string
        result += os.popen(cmd_string).read()
    else:
      cmd_string="/home/admin/bin/switch all > /tmp/watch"
      result += os.popen(cmd_string).read()

  return result

@app.route("/gui/reset")
def reset_demo():
  if (TESTBED == "hw"):
    cmd_string="cd ~/bin; ./demo-reset-hw.sh > /tmp/watch &"
  else:
    cmd_string="cd %s; ./demo-reset-sw.sh > /tmp/watch &" % (REMOTE_DEMO_SCRIPTS_DIR)
  os.popen(cmd_string)
  return "Reset" 

@app.route("/gui/scale")
def scale_demo():
  if (TESTBED == "hw"):
    cmd_string="cd ~/bin;  ~/bin/demo-scale-out-hw.sh > /tmp/watch &"
  else:
    cmd_string="cd %s; ./demo-scale-out-sw.sh > /tmp/watch &" % (REMOTE_DEMO_SCRIPTS_DIR)
  os.popen(cmd_string)
  return "scale"

@app.route("/gui/switch/<cmd>/<dpid>")
def switch_status_change(cmd, dpid):
  result = ""
  if (TESTBED == "hw"):
    return result

  r = re.compile(':')
  dpid = re.sub(r, '', dpid)
  host=controllers[0]
  cmd_string="ssh %s 'cd %s; ./switch.sh %s %s'" % (host, REMOTE_DEMO_SCRIPTS_DIR, dpid, cmd)
#  cmd_string="ssh -i ~/.ssh/onlabkey.pem %s 'cd %s; ./switch.sh %s %s'" % (host, REMOTE_DEMO_SCRIPTS_DIR, dpid, cmd)
  get_status="ssh -i ~/.ssh/onlabkey.pem %s 'cd %s; ./switch.sh %s'" % (host, REMOTE_DEMO_SCRIPTS_DIR, dpid)
  print "cmd_string"

  if cmd =="up" or cmd=="down":
    print "make dpid %s %s" % (dpid, cmd)
    os.popen(cmd_string)
    result=os.popen(get_status).read()

  return result

#* Link Up
#http://localhost:9000/gui/link/up/<src_dpid>/<src_port>/<dst_dpid>/<dst_port>
@app.route("/gui/link/up/<src_dpid>/<src_port>/<dst_dpid>/<dst_port>")
def link_up(src_dpid, src_port, dst_dpid, dst_port):
  result = ""

  if (TESTBED == "sw"):
    result = link_up_sw(src_dpid, src_port, dst_dpid, dst_port)
  else:
    result = link_up_hw(src_dpid, src_port, dst_dpid, dst_port)
  return result

# Link up on software testbed
def link_up_sw(src_dpid, src_port, dst_dpid, dst_port):

  cmd = 'up'
  result=""
  for dpid in (src_dpid, dst_dpid):
    if dpid in core_switches:
      host = controllers[0]
    else:
      hostid=int(dpid.split(':')[-2])
      host = controllers[hostid-1]

    if dpid == src_dpid:
      (port, dontcare) = get_link_ports(dpid, dst_dpid)
    else:
      (port, dontcare) = get_link_ports(dpid, src_dpid)

#    cmd_string="ssh -i ~/.ssh/onlabkey.pem %s 'cd %s; ./link.sh %s %s %s'" % (host, REMOTE_DEMO_SCRIPTS_DIR, dpid, port, cmd)
    cmd_string="ssh %s 'cd %s; ./link.sh %s %s %s'" % (host, REMOTE_DEMO_SCRIPTS_DIR, dpid, port, cmd)
    print cmd_string
    res=os.popen(cmd_string).read()
    result = result + ' ' + res

  return result

#      if hostid == 2 :
#        src_ports = [51]
#      else :
#        src_ports = [26]
#
#    for port in src_ports :
#      cmd_string="ssh -i ~/.ssh/onlabkey.pem %s 'cd %s; ./link.sh %s %s %s'" % (host, REMOTE_DEMO_SCRIPTS_DIR, dpid, port, cmd)
#      print cmd_string
#      res=os.popen(cmd_string).read()



# Link up on hardware testbed
def link_up_hw(src_dpid, src_port, dst_dpid, dst_port):

	port1 = src_port
	port2 = dst_port
	if src_dpid == "00:00:00:00:ba:5e:ba:11":
		if dst_dpid == "00:00:00:08:a2:08:f9:01":
			port1 = 24
			port2 = 24
		elif dst_dpid == "00:01:00:16:97:08:9a:46":
			port1 = 23
			port2 = 23
	elif src_dpid == "00:00:00:00:ba:5e:ba:13":
                if dst_dpid == "00:00:20:4e:7f:51:8a:35":
			port1 = 22
			port2 = 22
                elif dst_dpid == "00:00:00:00:00:00:ba:12":
			port1 = 23
			port2 = 23
	elif src_dpid == "00:00:00:00:00:00:ba:12":
                if dst_dpid == "00:00:00:00:ba:5e:ba:13":
			port1 = 23
			port2 = 23
                elif dst_dpid == "00:00:00:08:a2:08:f9:01":
			port1 = 22
			port2 = 22
                elif dst_dpid == "00:00:20:4e:7f:51:8a:35":
			port1 = 24
			port2 = 21
	elif src_dpid == "00:01:00:16:97:08:9a:46":
                if dst_dpid == "00:00:00:00:ba:5e:ba:11":
			port1 = 23
			port2 = 23
                elif dst_dpid == "00:00:20:4e:7f:51:8a:35":
			port1 = 24
			port2 = 24
	elif src_dpid == "00:00:00:08:a2:08:f9:01":
                if dst_dpid == "00:00:00:00:ba:5e:ba:11":
			port1 = 24
			port2 = 24
                elif dst_dpid == "00:00:00:00:00:00:ba:12":
			port1 = 22
			port2 = 22
                elif dst_dpid == "00:00:20:4e:7f:51:8a:35":
			port1 = 23
			port2 = 23
	elif src_dpid == "00:00:20:4e:7f:51:8a:35":
                if dst_dpid == "00:00:00:00:00:00:ba:12":
			port1 = 21
			port2 = 24
                elif dst_dpid == "00:00:00:00:ba:5e:ba:13":
			port1 = 22
			port2 = 22
                elif dst_dpid == "00:01:00:16:97:08:9a:46":
			port1 = 24
			port2 = 24
                elif dst_dpid == "00:00:00:08:a2:08:f9:01":
			port1 = 23
			port2 = 23

	cmd = 'up'
	result=""
	host = controllers[0]
	cmd_string="%s/link-hw.sh %s %s %s " % (ONS2014_DEMO_SCRIPTS_DIR, src_dpid, port1, cmd)
	print cmd_string
	res=os.popen(cmd_string).read()
	result = result + ' ' + res
	cmd_string="%s/link-hw.sh %s %s %s " % (ONS2014_DEMO_SCRIPTS_DIR, dst_dpid, port2, cmd)
	print cmd_string
	res=os.popen(cmd_string).read()
	result = result + ' ' + res


	return result


#* Link Down
#http://localhost:9000/gui/link/down/<src_dpid>/<src_port>/<dst_dpid>/<dst_port>
@app.route("/gui/link/<cmd>/<src_dpid>/<src_port>/<dst_dpid>/<dst_port>")
def link_down(cmd, src_dpid, src_port, dst_dpid, dst_port):

  if src_dpid in core_switches:
    host = controllers[0]
  else:
    hostid=int(src_dpid.split(':')[-2])
    host = controllers[hostid-1]

  if (TESTBED == "sw"):
    cmd_string="ssh %s 'cd %s; ./link.sh %s %s %s'" % (host, REMOTE_DEMO_SCRIPTS_DIR, src_dpid, src_port, cmd)
  else:
    if ( src_dpid == "00:00:00:08:a2:08:f9:01" ):
      cmd_string="%s/link-hw.sh %s %s %s " % (ONS2014_DEMO_SCRIPTS_DIR, dst_dpid, dst_port, cmd)
    else:
      cmd_string="%s/link-hw.sh %s %s %s " % (ONS2014_DEMO_SCRIPTS_DIR, src_dpid, src_port, cmd)
  print cmd_string

  result=os.popen(cmd_string).read()

  return result

#* Create Flow
#http://localhost:9000/gui/addflow/<src_dpid>/<src_port>/<dst_dpid>/<dst_port>/<srcMAC>/<dstMAC>
#1 FOOBAR 00:00:00:00:00:00:01:01 1 00:00:00:00:00:00:01:0b 1 matchSrcMac 00:00:00:00:00:00 matchDstMac 00:01:00:00:00:00
@app.route("/gui/addflow/<src_dpid>/<src_port>/<dst_dpid>/<dst_port>/<srcMAC>/<dstMAC>")
def add_flow(src_dpid, src_port, dst_dpid, dst_port, srcMAC, dstMAC):
  host = pick_host()
  url ="%s/wm/onos/flows/getsummary/%s/%s/json" % (host, 0, 0)
  (code, result) = get_json(url)
  parsedResult = json.loads(result)
  if len(parsedResult) > 0:
    if parsedResult[-1].has_key('flowId'):
      flow_nr = int(parsedResult[-1]['flowId']['value'], 16)
  else:
    flow_nr = -1  # first flow
    print "first flow"

  flow_nr += 1
  command =  "%s/add_flow.py -m onos %d %s %s %s %s %s matchSrcMac %s matchDstMac %s" % (ONOS_SCRIPTS_DIR, flow_nr, "dummy", src_dpid, src_port, dst_dpid, dst_port, srcMAC, dstMAC)
  flow_nr += 1
  command1 = "%s/add_flow.py -m onos %d %s %s %s %s %s matchSrcMac %s matchDstMac %s" % (ONOS_SCRIPTS_DIR, flow_nr, "dummy", dst_dpid, dst_port, src_dpid, src_port, dstMAC, srcMAC)
  print "add flow: %s, %s" % (command, command1)
  errcode = os.popen(command).read()
  errcode1 = os.popen(command1).read()
  ret=command+":"+errcode+" "+command1+":"+errcode1
  print ret 
  return ret

#* Delete Flow
#http://localhost:9000/gui/delflow/<flow_id>
@app.route("/gui/delflow/<flow_id>")
def del_flow(flow_id):
  command = "%s/delete_flow.py %s" % (ONOS_SCRIPTS_DIR, flow_id)
  print command
  errcode = os.popen(command).read()
  return errcode

#* Start Iperf Througput
#http://localhost:9000/gui/iperf/start/<flow_id>/<duration>
@app.route("/gui/iperf/start/<flow_id>/<duration>/<samples>")
def iperf_start(flow_id,duration,samples):
  url = "http://%s:%s/wm/onos/flows/get/%s/json" % (RestIP, RestPort, flow_id)
  try:
    response = urlopen(url)
    result = response.read()
    if len(result) == 0:
      print "No Flow found"
      return "Flow %s not found" % (flow_id);
  except:
    print "REST IF has issue %s" % url
    return "REST IF has issue %s" % url

  parsedResult = json.loads(result)

  flowId = int(parsedResult['flowId']['value'], 16)
  src_dpid = parsedResult['dataPath']['srcPort']['dpid']['value']
  src_port = parsedResult['dataPath']['srcPort']['port']['value']
  dst_dpid = parsedResult['dataPath']['dstPort']['dpid']['value']
  dst_port = parsedResult['dataPath']['dstPort']['port']['value']
#  print "FlowPath: (flowId = %s src = %s/%s dst = %s/%s" % (flowId, src_dpid, src_port, dst_dpid, dst_port)

  if src_dpid in core_switches:
      src_host = controllers[0]
  else:
      hostid=int(src_dpid.split(':')[-2])
      if TESTBED == "hw":
        src_host = "mininet%i" % hostid
      else:
        src_host = controllers[hostid-1]

  if dst_dpid in core_switches:
      dst_host = controllers[0]
  else:
      hostid=int(dst_dpid.split(':')[-2])
      if TESTBED == "hw":
        dst_host = "mininet%i" % hostid
      else:
        dst_host = controllers[hostid-1]

# /runiperf.sh <flowid> <src_dpid> <dst_dpid> hw:svr|sw:svr|hw:client|sw:client <proto>/<duration>/<interval>/<samples>
  protocol="udp"
  interval=0.1
  if TESTBED == "hw":
    cmd_string="dsh -w %s 'cd %s; " % (dst_host, REMOTE_DEMO_SCRIPTS_DIR)
  else:
    cmd_string="ssh %s 'cd %s; " % (dst_host, REMOTE_DEMO_SCRIPTS_DIR)
  cmd_string += "./runiperf.sh %d %s %s %s:%s %s/%s/%s/%s'" % (flowId, src_dpid, dst_dpid, TESTBED, "svr", protocol, duration, interval, samples)
  print cmd_string
  os.popen(cmd_string)

  if TESTBED == "hw":
    cmd_string="dsh -w %s 'cd %s; " % (src_host, REMOTE_DEMO_SCRIPTS_DIR)
  else:
    cmd_string="ssh %s 'cd %s;" % (src_host, REMOTE_DEMO_SCRIPTS_DIR)
  cmd_string+="./runiperf.sh %d %s %s %s:%s %s/%s/%s/%s'" % (flowId, src_dpid, dst_dpid, TESTBED, "client", protocol, duration, interval, samples)
  print cmd_string
  os.popen(cmd_string)

  return cmd_string


#* Get Iperf Throughput
#http://localhost:9000/gui/iperf/rate/<flow_id>
@app.route("/gui/iperf/rate/<flow_id>")
def iperf_rate(flow_id):
  url = "http://%s:%s/wm/onos/flows/get/%s/json" % (RestIP, RestPort, flow_id)
  try:
    response = urlopen(url)
    result = response.read()
    if len(result) == 0:
      return "no such iperf flow (flowid %s)" % flow_id
  except:
    print "REST IF has issue %s" % url
    exit

  parsedResult = json.loads(result)

  flowId = int(parsedResult['flowId']['value'], 16)
  src_dpid = parsedResult['dataPath']['srcPort']['dpid']['value']
  src_port = parsedResult['dataPath']['srcPort']['port']['value']
  dst_dpid = parsedResult['dataPath']['dstPort']['dpid']['value']
  dst_port = parsedResult['dataPath']['dstPort']['port']['value']

  if dst_dpid in core_switches:
    host = controllers[0]
  else:
    hostid=int(dst_dpid.split(':')[-2])
    if TESTBED == "hw":
      host = "mininet%i" % hostid
    else:
      host = controllers[hostid-1]

  url="http://%s:%s/log/iperfsvr_%s.out" % (host, 9000, flow_id)
  try:
    response = urlopen(url)
    result = response.read()
  except:
    print "REST IF has issue %s" % url
    return 

  if re.match("Cannot", result):
    resp = Response(result, status=400, mimetype='text/html')
    return "no iperf file found (host %s flowid %s): %s" % (host, flow_id, result)
  else:
    resp = Response(result, status=200, mimetype='application/json')
    return resp

if __name__ == "__main__":
  random.seed()
  read_config()
  read_link_def()
  if len(sys.argv) > 1 and sys.argv[1] == "-d":
    # for debugging
    #add_flow("00:00:00:00:00:00:02:02", 1, "00:00:00:00:00:00:03:02", 1, "00:00:00:00:02:02", "00:00:00:00:03:0c")
    #proxy_link_change("up", "00:00:00:00:ba:5e:ba:11", 1, "00:00:00:00:00:00:00:00", 1)
    #proxy_link_change("down", "00:00:20:4e:7f:51:8a:35", 1, "00:00:00:00:00:00:00:00", 1)
    #proxy_link_change("up", "00:00:00:00:00:00:02:03", 1, "00:00:00:00:00:00:00:00", 1)
    #proxy_link_change("down", "00:00:00:00:00:00:07:12", 1, "00:00:00:00:00:00:00:00", 1)
    #print "-- query all switches --"
    #query_switch()
    #print "-- query topo --"
    #topology_for_gui()
    ##print "-- query all links --"
    ##query_links()
    #print "-- query all devices --"
    #devices()
    #links()
    #switches()
    #reset_demo()
    pass
  else:
    app.debug = True
    app.run(threaded=True, host=ProxyIP, port=ProxyPort)
