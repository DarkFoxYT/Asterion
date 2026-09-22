const fs = require('node:fs');
const path = require('node:path');
fs.writeFileSync(path.join(__dirname, 'asterion_cutscene.js'), '// Generated standalone plugin; edit src/ and run node tools/blockbench/build.cjs.\n' +
    ['core.js', 'plugin.js'].map(file => fs.readFileSync(path.join(__dirname, 'src', file), 'utf8')).join('\n'));
