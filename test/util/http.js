const { expect } = require("chai");
const request = require('request');
const cfx_sdk = require('js-conflux-sdk');
const { Message } = require('js-conflux-sdk');

module.exports = {
    get,
    post,
    buildPostRequest,
    postNoCheck
}

function get(url, need_parse=false) {
    return new Promise((resolve, reject) => {
        request(url, (err, res, body) => {
            expect(!err).to.equal(true, `fail , ${url}, ${err}`);
            expect(res.statusCode).to.equal(200, `fail ${url}, http statusCode ${res.statusCode}`);
            handleResponse(body, need_parse, url, resolve, reject);
        });
    });
}

function handleResponse(body, need_parse, url, resolve, reject) {
    let data = body;
    if (need_parse) {
        let old = data;
        data = JSON.parse(data);
        expect(data.success).to.equal(true, url + ' ' + old);
    }
    resolve(data);
}

function buildPostRequest(url, obj) {
    return {
        url: url,
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(obj)
    };
}

function post(url, obj, need_parse=false) {
    return new Promise((resolve, reject) => {
        request.post(buildPostRequest(url, obj), (err, res, body) => {
            expect(!err).to.equal(true, `${url} request error ${err}`);
            expect(res.statusCode === 200 || res.statusCode === 201)
                .to.equal(true, `${url} return ${res.statusCode}`);
            handleResponse(body, need_parse, url, resolve, reject);
        });
    });
}


function postNoCheck(url, obj) {
    return new Promise((resolve, reject) => {
        request.post(buildPostRequest(url, obj),
            (err, res, body) => {
                if (err){
                    reject(err)
                } else {
                    // console.info(`place order result ${body}`)
                    resolve(JSON.parse(body))
                }
            })
    });
}
