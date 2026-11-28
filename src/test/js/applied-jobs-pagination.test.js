const assert = require("node:assert/strict");
const { test } = require("node:test");
const { getPage } = require("../../main/resources/static/applied-jobs-pagination.js");

test("returns the selected page and correct boundaries", () => {
  const jobs = Array.from({ length: 23 }, (_, index) => `job-${index + 1}`);

  assert.deepEqual(getPage(jobs, 2, 10), {
    items: jobs.slice(10, 20),
    page: 2,
    pageCount: 3,
    pageSize: 10,
    totalItems: 23,
    firstItem: 11,
    lastItem: 20
  });
  assert.deepEqual(getPage(jobs, 3, 10).items, jobs.slice(20));
  assert.equal(getPage(jobs, 3, 10).lastItem, 23);
});

test("clamps requested pages when a list shrinks or a page is out of range", () => {
  const jobs = ["job-1", "job-2", "job-3"];

  assert.equal(getPage(jobs, 8, 2).page, 2);
  assert.deepEqual(getPage(jobs, 0, 2).items, ["job-1", "job-2"]);
});

test("handles empty lists and invalid page sizes", () => {
  assert.deepEqual(getPage([], 4, 10), {
    items: [],
    page: 1,
    pageCount: 0,
    pageSize: 10,
    totalItems: 0,
    firstItem: 0,
    lastItem: 0
  });
  assert.equal(getPage(Array.from({ length: 11 }), 1, 0).pageCount, 2);
});