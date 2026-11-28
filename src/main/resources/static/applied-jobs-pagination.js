(function (root) {
  const DEFAULT_PAGE_SIZE = 10;

  function getPage(items, requestedPage, requestedPageSize) {
    const allItems = Array.isArray(items) ? items : [];
    const pageSize = Number.isInteger(requestedPageSize) && requestedPageSize > 0
      ? requestedPageSize
      : DEFAULT_PAGE_SIZE;
    const pageCount = Math.ceil(allItems.length / pageSize);
    const page = Math.min(
      Math.max(Number.isInteger(requestedPage) ? requestedPage : 1, 1),
      Math.max(pageCount, 1)
    );
    const startIndex = (page - 1) * pageSize;
    const endIndex = Math.min(startIndex + pageSize, allItems.length);

    return {
      items: allItems.slice(startIndex, endIndex),
      page,
      pageCount,
      pageSize,
      totalItems: allItems.length,
      firstItem: allItems.length === 0 ? 0 : startIndex + 1,
      lastItem: endIndex
    };
  }

  const pagination = { getPage };
  if (typeof module === "object" && module.exports) {
    module.exports = pagination;
  } else {
    root.AppliedJobsPagination = pagination;
  }
})(globalThis);