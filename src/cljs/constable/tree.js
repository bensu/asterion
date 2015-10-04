goog.provide('constable.tree');

goog.require('goog.array');
goog.require('cljsjs.d3');
goog.require('constable.search');

constable.tree.config = {w: 1600, h: 1200,
                         rx: 60, ry: 30,
                         fill: d3.scale.category20()};

// TODO: svg should take the whole background
constable.tree.svg = function(nodeId) {
    return d3.select(nodeId)
    		   .attr("width", 1535)
                   .attr("height", 876);
                // .attr("width", constable.tree.config.w)
        	// .attr("height", constable.tree.config.h);
};

constable.tree.formatNs = function(s) {
    return s.split(".").join("\n");
};

constable.tree.colors = palette('tol',11); 

constable.tree.nodeToGroup = function(name) {
    return name.split("\.")[0];
};

constable.tree.Graph = function(nsOpts, json) {
   var g = new dagreD3.graphlib.Graph().setGraph({}); 

   var highlightNs = nsOpts.highlighted;

   var allColors = constable.tree.colors.slice(0);
   var nodeColors = {};
   json.nodes.forEach(function(node) {
       var group = constable.tree.nodeToGroup(node.name);
       var color;
       if (typeof nodeColors[group] !== "undefined") {
           color = nodeColors[group];
       } else {
           color = allColors[allColors.length - 1];
           nodeColors[group] = color;
           allColors.pop();
       }
       var hgStyle = "";
       var labelStyle = "";
       if (highlightNs && (goog.array.contains(highlightNs, node.name))) {
           labelStyle = "fill:#f0f1eb";
           hgStyle = "fill:black;";
       } 
       g.setNode(node.name,
                 {label: node.name,
                  labelStyle: labelStyle, 
                  style: "fill:#" + color + ";stroke:black;" + hgStyle});
       });
       
   json.edges.forEach(function(edge) {
       g.setEdge(edge.source, edge.target,{});
   });
   g.nodes().forEach(function(v) {
      var node = g.node(v);
      node.rx = constable.tree.config.rx;
      node.ry = constable.tree.config.ry;
   });
   return g;
};

constable.tree.drawTree = function(nodeId, nsOpts, json) {
  var g = constable.tree.Graph(nsOpts, json);
  var root = constable.tree.svg(nodeId);
  var inner = root.select("g");
  var zoom = d3.behavior.zoom().on("zoom", function() {
      inner.attr("transform", "translate(" + d3.event.translate + ")" +
                             "scale(" + d3.event.scale + ")");
  });
  root.call(zoom);
  var render = new dagreD3.render();
  render(inner, g);

  var initialScale = 0.75;
  zoom.translate([(root.attr("width") - g.graph().width * initialScale) / 2, 20])
      .scale(initialScale)
      .event(root);
  // root.attr("height", g.graph().height * initialScale + 40);
};
