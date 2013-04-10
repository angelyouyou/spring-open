


(function () {

var projection = d3.geo.mercator()
    .center([82, 46])
    .scale(8000)
    .rotate([-180,0]);

function createMap(svg, cb) {
	topology = svg.append('svg:svg').attr('id', 'viewBox').attr('viewBox', '0 0 1000 1000').
			attr('id', 'viewbox');

	var map = topology.append("g").attr('id', 'map');

	var path = d3.geo.path().projection(projection);

	d3.json('data/world.json', function(error, topology) {
		map.selectAll('path')
			.data(topojson.object(topology, topology.objects.world).geometries)
		    	.enter()
		      		.append('path')
		      		.attr('d', path)

		cb();
	});
}

/***************************************************************************************************

***************************************************************************************************/
var switchMap;
function makeSwitchMap() {
	switchMap = {};
	model.coreSwitches.forEach(function (s) {
		switchMap[s.dpid] = s;
	});
	model.aggregationSwitches.forEach(function (s) {
		switchMap[s.dpid] = s;
	});
	model.edgeSwitches.forEach(function (s) {
		switchMap[s.dpid] = s;
	});
}

/***************************************************************************************************
create a map from edge->aggregation and aggreation->core switches
***************************************************************************************************/
var switchAssociations;
function makeAssociations() {
	switchAssociations = {};

	var key;
	for (key in model.configuration.association) {
		var aggregationSwitches = model.configuration.association[key];
		aggregationSwitches.forEach(function (s) {
			switchAssociations[s] = key;
		});
	}
}

/***************************************************************************************************
get the upstream switch. this only makes sense for aggregation and edge switches
***************************************************************************************************/
function getUpstream(dpid, className) {
	if (className === 'aggregation') {
		return switchAssociations[dpid];
	} else if (className === 'edge') {
		var aggregationDpid = dpid.split(':');
		aggregationDpid[7] = '01'; // the last component of the agg switch is always '01'
		return aggregationDpid.join(':');
	}
}



/*****************a**********************************************************************************
create a map to hold the fanout information for the switches
***************************************************************************************************/
var fanouts;
function makeFanouts() {
	fanouts = {};
	model.coreSwitches.forEach(function (s) {
		fanouts[s.dpid] = model.configuration.geo[s.dpid];
		fanouts[s.dpid].count = 0;
	});

	model.aggregationSwitches.forEach(function (s) {
		fanouts[s.dpid] = {count: 0};
		var upstreamFanout = fanouts[getUpstream(s.dpid, 'aggregation')];
		upstreamFanout.count += 1;
	});

	model.edgeSwitches.forEach(function (s) {
		fanouts[s.dpid] = {};
		var upstreamFanout = fanouts[getUpstream(s.dpid, 'edge')];
		upstreamFanout.count += 1;
	});
}


var projection;
var switchLayer;
var labelsLayer;
var linksLayer;
createTopologyView = function (cb) {
	var svg = createRootSVG();

	createMap(svg, function () {
		switchLayer = topology.append('g');
		labelsLayer = topology.append('g');
		linksLayer = topology.append('g');

		cb();
	});
}

function updateLinkLines() {

	// key on link dpids since these will come/go during demo
	var linkLines = linksLayer.selectAll('.link').data(links, function (d) {
		return d['src-switch']+'->'+d['dst-switch'];
	});

	// add new links
	linkLines.enter().append("svg:path").attr("class", "link");

	linkLines.attr('id', function (d) {
			return makeLinkKey(d);
		}).attr("d", function (d) {
			var src = d3.select(document.getElementById(d['src-switch']));
			var dst = d3.select(document.getElementById(d['dst-switch']));

			if (src.empty() || dst.empty()) {
				return "M0,0";
			}

			var srcPt = document.querySelector('svg').createSVGPoint();
			srcPt.x = src.attr('x');
			srcPt.y = src.attr('y');

			var dstPt = document.querySelector('svg').createSVGPoint();
			dstPt.x = dst.attr('x');
			dstPt.y = dst.attr('y');

			var midPt = document.querySelector('svg').createSVGPoint();
			midPt.x = (srcPt.x + dstPt.x)/2;
			midPt.y = (srcPt.y + dstPt.y)/2;

			return line([srcPt, midPt, dstPt]);
		})
		.attr("marker-mid", function(d) { return "url(#arrow)"; })
		.classed('pending', function (d) {
			return d.pending;
		});

	// remove old links
	linkLines.exit().remove();
}

var fanOutAngles = {
	aggregation: 90,
	edge: 7
}

function makeSwitchesModel(switches, className) {
	var switchesModel = [];
	switches.forEach(function (s) {
		var geo = model.configuration.geo[s.dpid];

		var pos, label;
		if (geo) {
			pos = projection([geo.lng, geo.lat]);
			label = geo.label;
		} else {
			var upstream = getUpstream(s.dpid, className);
			if (upstream) {
				var upstreamGeo = fanouts[upstream];
				pos = projection([upstreamGeo.lng, upstreamGeo.lat]);

				var fanOutAngle = upstreamGeo.fanOutAngle;
				fanOutAngle -= (upstreamGeo.count - 1) * fanOutAngles[className]/2;

				var angle = toRadians(fanOutAngle);
				var xOff = Math.sin(angle) * widths[className] * 20;
				var yOff = Math.cos(angle) * widths[className] * 20;

				pos = [pos[0] + xOff, pos[1] + yOff];

				var fakeGeo = projection.invert(pos);

				var fanout = fanouts[s.dpid];
				fanout.fanOutAngle = fanOutAngle;
				fanout.lng = fakeGeo[0];
				fanout.lat = fakeGeo[1];

				upstreamGeo.fanOutAngle += fanOutAngles[className];

			} else {
				pos = projection([-98, 39]);
			}
		}

		switchesModel.push({
			dpid: s.dpid,
			state: s.state,
			className: className,
			controller: s.controller,
			label: label,
			x: pos[0],
			y: pos[1]
		});
	});

	return switchesModel;
}

function switchEnter(d) {
	var g = d3.select(this);
	var width = widths[d.className];

	g.append('svg:circle').attr('r', width)
		.classed(d.className, true)
		.attr('cx', d.x)
		.attr('cy', d.y);

	if (d.label) {
		g.append('svg:text')
			.text(d.label)
			.attr('x', d.x + width)
			.attr('y', d.y + width);
	}
}

function switchesEnter(switches) {
	return switchLayer.selectAll('g').data(switches, function (d) {
		return d.dpid;
	})
		.enter()
			.append('svg:g')
				.attr("id", function (d) {
					return d.dpid;
				})
				.attr('x', function (d) {
					return d.x;
				})
				.attr('y', function (d) {
					return d.y;
				})
				.each(switchEnter);
}

// function labelsEnter(className, model) {
// 			.enter().append("svg:g")
// 			.classed('nolabel', true)
// 			.attr("id", function (data) {
// 				return data.dpid + '-label';
// 			})


// 	return topology.selectAll('.'+className).data(makeSwitchesModel(model))
// 		.enter()
// 			.append('svg:g')
// 				.attr("id", function (d) {
// 					return d.dpid;
// 				})
// 				.classed('core', true)
// 				.attr('x', function (d) {
// 					return d.x;
// 				})
// 				.attr('y', function (d) {
// 					return d.y;
// 				})
// 				.each(switchEnter);
// }

function switchesUpdate(switches) {
	switches.each(function (d) {
			// if there's a pending state changed and then the state changes, clear the pending class
			var circle = d3.select(this);
			if (d.state === 'ACTIVE' && circle.classed('inactive') ||
				d.state === 'INACTIVE' && circle.classed('active')) {
				circle.classed('pending', false);
			}
		})
		.attr('class', function (d)  {
			if (d.state === 'ACTIVE' && d.controller) {
				return 'active ' + controllerColorMap[d.controller];
			} else {
				return 'inactive ' + 'colorInactive';
			}
		});
}

drawTopology = function () {

	makeSwitchMap();
	makeAssociations();
	makeFanouts();

	var coreSwitches = makeSwitchesModel(model.coreSwitches, 'core');
	var aggregationSwitches = makeSwitchesModel(model.aggregationSwitches, 'aggregation');
	var edgeSwitches = makeSwitchesModel(model.edgeSwitches, 'edge');

	var switches = coreSwitches.concat(aggregationSwitches).concat(edgeSwitches);

	switchesUpdate(switchesEnter(switches));





	updateLinkLines();
}

})();