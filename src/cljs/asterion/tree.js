goog.provide('asterion.tree');

goog.require('goog.array');
goog.require('asterion.d3');
goog.require('asterion.ring');

asterion.tree.config = function() {
    return {
        w: window.innerWidth,
        h: window.outerHeight,
        rx: 60,
        ry: 30,
        fill: d3.scale.category20()
    };
};

asterion.tree.svg = function(nodeId) {
    var config =  asterion.tree.config();
    return d3.select(nodeId).attr("width", config.w).attr("height", config.h);
};

asterion.tree.formatNs = function(s) {
    return s.split(".").join("\n");
};

asterion.tree.colors = new asterion.ring.Ring(palette('tol-rainbow',12).reverse());

asterion.tree.groupToColor = asterion.ring.memoize(asterion.tree.colors);

asterion.tree.nodeToGroup = function(name) {
    // TODO: looking at the second token works for libraries, not for apps
    var parts = name.split("\.");
    if (parts.length > 1) {
        return parts[1].replace("-","");
    } else {
        return name;
    }
};

asterion.tree.nodeToSubGroup = function(name) {
    var tokens = name.split("\.");
    return (tokens.length > 2) ? tokens[1] : null;
};

asterion.tree.Graph = function(json) {
   var config = asterion.tree.config();
   var g = new dagreD3.graphlib.Graph().setGraph({});

   json.nodes.forEach(function(node) {
       var group = asterion.tree.nodeToGroup(node.name);
       var color = asterion.tree.groupToColor(group);
       var hgStyle = "";
       var labelStyle = "";
       if (node.highlight) {
           labelStyle = "fill:#f0f1eb";
           hgStyle = "fill:black;";
       }
       g.setNode(node.name, {
           label: node.name,
           labelStyle: labelStyle,
           style: "fill:#" + color + ";stroke:black;" + hgStyle});
       });

   json.edges.forEach(function(edge) {
       g.setEdge(edge.source, edge.target,{});
   });
   g.nodes().forEach(function(v) {
      var node = g.node(v);
      if (typeof node !== 'undefined') {
          node.rx = config.rx;
          node.ry = config.ry;
      }
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
