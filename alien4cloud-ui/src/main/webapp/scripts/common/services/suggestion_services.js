define(function (require) {
  'use strict';

  var modules = require('modules');

  modules.get('a4c-common').factory('suggestionServices', ['$resource', function($resource) {
    var tagSuggestionResource = $resource('rest/suggest/tag/:path/:searchText', {}, {
      'get' : {
        method : 'GET'
      }
    });

    var genericSuggestionDAO = $resource('rest/suggestions/:index/:type/:path', {
      index : '@index',
      type : '@type',
      path : '@path'
    });

    var getSuggestions = function(index, type, path, text) {
      return genericSuggestionDAO.get({
        index : index,
        type : type,
        path : path,
        text : text
      }).$promise.then(function(result) {
        return result.data;
      });
    };

    var getTagNameSuggestions = function(keyword) {
      return tagSuggestionResource.get({
        path : 'name',
        searchText : keyword
      }).$promise.then(function(result) {
        var formatedData = result.data;
        formatedData.sort();
        return formatedData;
      });
    };

    return {
      tagNameSuggestions : getTagNameSuggestions,
      getSuggestions : getSuggestions
    };

  }]); // factory
}); // define
