goog.provide('asterion.ring');

/**
 * Class for Ring
 * @param initArray
 * @constructor
 * @template T
*/
asterion.ring.Ring = function(initArray) {
    /**
     * Internal index
     * @private {number}
     */
    this.index = 0;
    /**
     * Underlying array
     * @private {!Array.<T>}
    */
    this.array = initArray.slice();
};

asterion.ring.Ring.prototype.next = function() {
    this.index = (this.index === this.array.length) ? 0 : (this.index + 1);
    return this.array[this.index]; 
};

/**
 * @param Ring
*/
asterion.ring.memoize = function(initRing) {
    var dict = {}; 
    return function(val) {
       if (typeof dict[val] === "undefined") {
           dict[val] = initRing.next();
       };
       return dict[val];
    };
};

