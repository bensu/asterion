# Asterion

Make and explore dependency graphs for Clojure(Script) projects.

> La casa es del tamaño del mundo; mejor dicho, es el mundo.

Asterion will run `tools.namespace` against your project sources,
generate a dependency graph, and then graph it using something similar
to [graphviz](https://github.com/cpettitt/dagre-d3). To get your
project sources, it will try to parse your `project.clj` and if that
works, the graph is displayed, you can navigate it (zoom),
filter out namespaces by their names, and highlight namespaces that
contain a certain word in their file (full text search with grep).

Here is an initial [demo](https://www.youtube.com/watch?v=NOJLmkIyS2A).
and

## Usage

Asterion is currently structured as a webapp, we'll see how it can be
packaged in the future. You can try it [here](http://asterion-dev.elasticbeanstalk.com/index.html)

To use it locally:

```sh
# Compile ClojureScript
lein cljsbuild once production
# Start the server
lein ring server
```

## Developing

### Special Requirements

First install an experimental version of `tools.namespace`:

```sh
https://github.com/bensu/tools.namespace
cd tools.namespace
git checkout node
mvn install
```

If it worked, you should have a `0.3.19-SNAPSHOT` folder under:

```sh
ls ~/.m2/repository/org/clojure/tools.namespace/
```

With `tools.namespace` installed you can start the Electron app as
usual.

## Development mode

Start the figwheel server:

```
lein figwheel
```

If you are on OSX/Linux and have `rlwrap` installed, you can start the figwheel server with:

```
rlwrap lein figwheel
```

This will give better readline support.

More about [figwheel](https://github.com/bhauman/lein-figwheel) here.


In another terminal window, launch the electron app:

```
grunt launch
```

You can edit the `src/cljs/draft/core.cljs` file and the changes should show up in the electron app without the need to re-launch.

### Dependencies

Node dependencies are in `package.json` file. Bower dependencies are in `bower.json` file. Clojure/ClojureScript dependencies are in `project.clj`.
