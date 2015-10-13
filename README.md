# Asterion 

Make and explore dependency graphs for Clojure(Script) projects.

> La casa es del tamaño del mundo; mejor dicho, es el mundo.

Asterion will run `tools.namespace` against your project sources,
generate a dependency graph, and then graph it using something similar
to [graphviz](https://github.com/cpettitt/dagre-d3). To get your
project sources, it will try to parse your `project.clj` and if that
doesn't work (it mostly doesn't!) it will ask you to input the sources
manually. Once the graph is displayed, you can navigate it (zoom),
filter out namespaces by their names, and highlight namespaces that
contain a certain word in their file (full text search with grep).

Here is an initial [demo](https://www.youtube.com/watch?v=NOJLmkIyS2A).

## Usage

The binary is currently only released for Linux. Follow
[these](https://github.com/bensu/asterion/releases/tag/v0.1.0-alpha1)
instructions to run it.

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

### Requirements

* JDK 1.7+
* Leiningen 2.5.1
* io.js 3.1.0 [This is done to match the verion of io.js being used in Electron v0.31.0]
* [NSIS](http://nsis.sourceforge.net/) (*Windows only*)

On Mac/Linux, installing io.js using [Node Version Manager](https://github.com/creationix/nvm) is recommended.

This project uses Electron v0.31.0. Please check [Electron's GH site](https://github.com/atom/electron) for the latest version. The version is specified in `Gruntfile.js` under the `Grunt Config` section.

### Setup

On Mac/Linux:

```
scripts/setup.sh
```

On Windows:

```
scripts\setup.bat
```

This will install the node dependencies for the project, along with grunt and bower and will also run `grunt setup`.


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
