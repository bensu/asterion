goog.provide('asterion.tree');

goog.require('goog.array');
goog.require('asterion.d3');

asterion.tree.config = {w: screen.width,
                        h: screen.height,
                        rx: 60,
                        ry: 30,
                        fill: d3.scale.category20()};

asterion.tree.svg = function(nodeId) {
    return d3.select(nodeId)
                .attr("width", asterion.tree.config.w)
        	.attr("height", asterion.tree.config.h);
};

asterion.tree.formatNs = function(s) {
    return s.split(".").join("\n");
};

asterion.tree.colors = palette('tol',11); 

asterion.tree.nodeToGroup = function(name) {
    return name.split("\.")[0];
};

asterion.tree.Graph = function(json) {
   var g = new dagreD3.graphlib.Graph().setGraph({}); 

   var allColors = asterion.tree.colors.slice(0);
   var nodeColors = {};
   json.nodes.forEach(function(node) {
       var group = asterion.tree.nodeToGroup(node.name);
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
       if (node.highlight) {
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
      node.rx = asterion.tree.config.rx;
      node.ry = asterion.tree.config.ry;
   });
   return g;
};

asterion.tree.drawTree = function(nodeId, nsOpts, json) {
  var g = asterion.tree.Graph(nsOpts, json);
  var root = asterion.tree.svg(nodeId);
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
