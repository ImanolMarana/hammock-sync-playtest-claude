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

package org.hammock.sync.internal.replication;

import org.hammock.common.RequireRunningCouchDB;
import org.hammock.sync.internal.mazha.Response;
import org.hammock.sync.documentstore.ConflictResolver;
import org.hammock.sync.internal.documentstore.DatabaseImpl;
import org.hammock.sync.documentstore.DocumentBodyFactory;
import org.hammock.sync.documentstore.DocumentRevision;
import org.hammock.sync.documentstore.DocumentStore;
import org.hammock.sync.documentstore.UnsavedStreamAttachment;
import org.hammock.sync.util.TestUtils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tomblench on 14/11/2014.
 */

@Category(RequireRunningCouchDB.class)
public class AttachmentsConflictsTest extends ReplicationTestBase {

    // test that we can correctly pull attachments for a conflicted remote db
    @Test
    public void testConflictedAttachment() throws Exception {
        // create remote
        Map<String, Object> foo1 = new HashMap<String, Object>();
        foo1.put("_id", "doc-a");
        Map<String, Object> atts = new HashMap<String, Object>();
        Map<String, String> att = new HashMap<String, String>();
        foo1.put("_attachments", atts);
        atts.put("att1", att);
        att.put("content_type", "text/plain");
        // the string "hello" base64 encoded
        att.put("data", "aGVsbG8=");
        foo1.put("foo", "(from remoteDb)");
        Response response = remoteDb.create(foo1);

        // create local (1-rev and 2-rev)
        DocumentRevision rev = new DocumentRevision("doc-a");
        rev.setBody(DocumentBodyFactory.create("{\"foo\": \"local\"}".getBytes()));
        DocumentRevision rev2 = this.datastore.create(rev);
        rev2.getAttachments().put("att1", new UnsavedStreamAttachment(
                        new ByteArrayInputStream("hello universe".getBytes()),
                        "text/plain")
        );
        this.datastore.update(rev2);
        this.push();
        this.documentStore.delete();
        this.documentStore = DocumentStore.getInstance(new File(this.datastoreManagerPath, "foo-bar-baz"));
        this.datastore = (DatabaseImpl) documentStore.database();
        try {
            this.pull();

            DocumentRevision gotRev = this.datastore.read("doc-a");

            Assert.assertEquals(gotRev.getAttachments().size(), 1);
            // local one is guaranteed to be winner because its revision tree is longer
            Assert.assertEquals(gotRev.getBody().asMap().get("foo"), "local");
            Assert.assertFalse(TestUtils.streamsEqual(gotRev.getAttachments().get("att1").getInputStream(),

                    new ByteArrayInputStream("hello".getBytes())));
            Assert.assertTrue(TestUtils.streamsEqual(gotRev.getAttachments().get("att1")
                    .getInputStream(),
                    new ByteArrayInputStream("hello universe".getBytes())));
        } finally {
            this.datastore.close();
        }
    }

    // test that we can correctly pull attachments for a resolved remote db
    @Test
    public void testResolvedAttachment() throws Exception {
        // create remote
        Map<String, Object> foo1 = new HashMap<String, Object>();
        foo1.put("_id", "doc-a");
        Map<String, Object> atts = new HashMap<String, Object>();
        Map<String, String> att = new HashMap<String, String>();
        foo1.put("_attachments", atts);
        atts.put("att1", att);
        att.put("content_type", "text/plain");
        // the string "hello" base64 encoded
        att.put("data", "aGVsbG8=");
        foo1.put("foo", "(from remoteDb)");
        Response response = remoteDb.create(foo1);

        // create local
        DocumentRevision rev = new DocumentRevision("doc-a");
        rev.setBody(DocumentBodyFactory.create("{\"foo\": \"local\"}".getBytes()));
        rev.getAttachments().put("att1", new UnsavedStreamAttachment(
                        new ByteArrayInputStream("hello universe".getBytes()),
                        "text/plain")
        );
        this.datastore.create(rev);
        this.pull();
        this.datastore.resolveConflicts("doc-a", new ConflictResolver() {
            @Override
            public DocumentRevision resolve(String docId, List<? extends DocumentRevision> conflicts) {
                return conflicts.get(0);
            }
        });
        this.push();
        this.pull();
        this.documentStore.delete();
        this.documentStore = DocumentStore.getInstance(new File(this.datastoreManagerPath, "foo-bar-baz"));
        this.datastore = (DatabaseImpl) documentStore.database();
        try {
            this.pull();

            DocumentRevision gotRev = this.datastore.read("doc-a");
            Assert.assertEquals(gotRev.getAttachments().size(), 1);
        } finally {
            this.datastore.close();
        }
    }

}
