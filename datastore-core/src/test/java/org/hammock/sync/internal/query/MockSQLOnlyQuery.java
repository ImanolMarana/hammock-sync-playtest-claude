/*
 * Copyright © 2017 IBM Corp. All rights reserved.
 *
 * Copyright © 2015 Cloudant, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package org.hammock.sync.internal.query;

import org.hammock.sync.query.FieldSort;
import org.hammock.sync.query.Index;
import org.hammock.sync.query.QueryException;
import org.hammock.sync.query.QueryResult;
import org.hammock.sync.internal.util.Misc;
import org.hammock.sync.util.TestUtils;

import java.util.List;
import java.util.Map;

/**
 * This sub class of the {@link QueryImpl} along with
 * {@link MockSQLOnlyQueryExecutor} is used by query
 * executor tests to force the tests to exclusively exercise the SQL engine logic.
 * This class is used for testing purposes only.
 *
 * @see QueryImpl
 * @see MockSQLOnlyQueryExecutor
 */
public class MockSQLOnlyQuery extends DelegatingMockQuery {

    public MockSQLOnlyQuery(QueryImpl im) {
        super(im);
    }

    /*
     * Override this variant of find to use a MockSQLOnlyQueryExecutor.
     */
    @Override
    public QueryResult find(Map<String, Object> query,
                            long skip,
                            long limit,
                            List<String> fields,
                            List<FieldSort> sortSpecification) throws QueryException {
        Misc.checkNotNull(query, "query");

        refreshAllIndexes();

        MockSQLOnlyQueryExecutor queryExecutor = null;
        try {
            queryExecutor = new MockSQLOnlyQueryExecutor(delegate.getDatabase(),
                    TestUtils.getDBQueue(delegate));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<Index> indexes = listIndexes();
        return queryExecutor.find(query, indexes, skip, limit, fields, sortSpecification);
    }
}
