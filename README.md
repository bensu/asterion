# Asterion 

A tool to make and explore dependency graphs for Clojure(Script) projects.

NOTE: do not use this! It is under development and has dependencies
that have not been published.

## Requirements

* JDK 1.7+
* Leiningen 2.5.1
* io.js 3.1.0 [This is done to match the verion of io.js being used in Electron v0.31.0]
* [NSIS](http://nsis.sourceforge.net/) (*Windows only*)

On Mac/Linux, installing io.js using [Node Version Manager](https://github.com/creationix/nvm) is recommended.

This project uses Electron v0.31.0. Please check [Electron's GH site](https://github.com/atom/electron) for the latest version. The version is specified in `Gruntfile.js` under the `Grunt Config` section.

## Setup

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

## Dependencies

Node dependencies are in `package.json` file. Bower dependencies are in `bower.json` file. Clojure/ClojureScript dependencies are in `project.clj`.
