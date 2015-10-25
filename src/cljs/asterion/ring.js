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

/**
 * Gets the next value in the ring
 **/
asterion.ring.Ring.prototype.next = function() {
    this.index = (this.index < this.array.length) ? this.index : 0;
    var out = this.array[this.index]; 
    this.index = this.index + 1;
    return out; 
};

/**
 * @param {Ring} initRing
 * @return {Function}
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

