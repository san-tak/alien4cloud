'use strict';

// Reads HTML for usemin blocks to enable smart builds that automatically concat, minify and revision files.
// Creates configurations in memory so additional tasks can operate on them
module.exports = {
  html: '<%= yeoman.app %>/index.html',
  options: {
    root: '<%= yeoman.app %>',
    dest: '<%= yeoman.dist %>'
  }
};