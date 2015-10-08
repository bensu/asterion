var app = require('app'),
    BrowserWindow = require('browser-window'),
    fs = require('fs-plus'),
    ipc = require('ipc'),
    Menu = require('menu'),
    path = require('path'),
    dialog = require('dialog'),
    shell = require('shell'),
    child_process = require('child_process'),
    packageJson = require(__dirname + '/package.json');

// Report crashes to atom-shell.
require('crash-reporter').start();

const devConfigFile = __dirname + '/config.json';
var devConfig = {};
if (fs.existsSync(devConfigFile)) {
  devConfig = require(devConfigFile);
}


const isDev = (packageJson.version.indexOf("DEV") !== -1);
const onMac = (process.platform === 'darwin');
const acceleratorKey = onMac ? "Command" : "Control";
const isInternal = (devConfig.hasOwnProperty('internal') && devConfig['internal'] === true);



// Keep a global reference of the window object, if you don't, the window will
// be closed automatically when the javascript object is GCed.
var mainWindow = null;

// make sure app.getDataPath() exists
// https://github.com/oakmac/cuttle/issues/92
if (!fs.isDirectorySync(app.getDataPath())) {
  fs.mkdirSync(app.getDataPath());
}


//------------------------------------------------------------------------------
// Main
//------------------------------------------------------------------------------

const versionString = "Version   " + packageJson.version + "\nDate       " + packageJson["build-date"] + "\nCommit  " + packageJson["build-commit"];


function showVersion() {
  dialog.showMessageBox({type: "info", title: "Version", buttons: ["OK"], message: versionString});
}

var fileMenu = {
  label: 'File',
  submenu: [
  {
    label: 'Quit',
    accelerator: acceleratorKey + '+Q',
    click: function ()
    {
      app.quit();
    }
  }]
};

var helpMenu = {
  label: 'Help',
  submenu: [
  {
    label: 'Version',
    click: showVersion
  }]
};

var debugMenu = {
  label: 'Debug',
  submenu: [
  {
    label: 'Toggle DevTools',
    click: function ()
    {
      mainWindow.toggleDevTools();
    }
  }
  ]
};

var menuTemplate = [fileMenu, debugMenu, helpMenu];


// NOTE: not all of the browserWindow options listed on the docs page work
// on all operating systems
const browserWindowOptions = {
  height: 850,
  title: 'asterion',
  width: 1400,
  icon: __dirname + '/img/logo_96x96.png'
};


//------------------------------------------------------------------------------
// Register IPC Calls from the Renderers
//------------------------------------------------------------------------------

// Open files
const projectDialogOpts = { 
  title: 'Please select an existing project.clj file',
  properties: ['openFile'],
  filters: [
    {
      name: 'Leiningen project.clj',
      extensions: ['boot', 'xml', 'clj']
    }
  ]
};

function addProjectDialog(event, platform) {
  dialog.showOpenDialog(projectDialogOpts, function(filenames) {
    if (filenames) {
      var filename = filenames[0];
      mainWindow.webContents.send('add-project-success', filename, platform);
    }
  });
}

ipc.on('request-project-dialog', addProjectDialog);

// grep files

function grepWithFork(event, filenames, stringPattern) {
    var cmd = "egrep -Rl -m 100 '"
               + stringPattern
               + "' "
               + filenames.join(" ");
    child_process.exec(cmd, {maxBuffer: 200000000}, function(err, stdout) {
        if (err) {
            console.log("There was an error:\n");
            process.stdout.write(stdout);
            mainWindow.webContents.send('search-error',err);
        } else {
            mainWindow.webContents.send('search-success',stdout.split(/\n/));
        }
    });
}

ipc.on('request-search', grepWithFork);

//------------------------------------------------------------------------------
// Ready
//------------------------------------------------------------------------------


// This method will be called when atom-shell has done everything
// initialization and ready for creating browser windows.
app.on('ready', function() {
  // Create the browser window.
  mainWindow = new BrowserWindow(browserWindowOptions);

  // and load the index.html of the app.
  mainWindow.loadUrl('file://' + __dirname + '/index.html');

  var menu = Menu.buildFromTemplate(menuTemplate);

  Menu.setApplicationMenu(menu);

  // Emitted when the window is closed.
  mainWindow.on('closed', function() {
    // Dereference the window object, usually you would store windows
    // in an array if your app supports multi windows, this is the time
    // when you should delete the corresponding element.
    mainWindow = null;
    app.quit();
  });

  if (devConfig.hasOwnProperty('dev-tools') && devConfig['dev-tools'] === true) {
    mainWindow.openDevTools();
  }

});
