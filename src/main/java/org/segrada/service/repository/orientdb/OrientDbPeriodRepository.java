package org.segrada.service.repository.orientdb;

import com.google.inject.Inject;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.segrada.model.Period;
import org.segrada.model.prototype.IPeriod;
import org.segrada.service.repository.PeriodRepository;
import org.segrada.service.repository.orientdb.base.AbstractSegradaOrientDbRepository;
import org.segrada.service.util.AbstractLazyLoadedObject;
import org.segrada.session.ApplicationSettings;
import org.segrada.session.Identity;

import java.util.LinkedList;
import java.util.List;

/**
 * Copyright 2015 Maximilian Kalus [segrada@auxnet.de]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * OrientDb Period Repository
 */
public class OrientDbPeriodRepository extends AbstractSegradaOrientDbRepository<IPeriod> implements PeriodRepository {
	/**
	 * Constructor
	 */
	@Inject
	public OrientDbPeriodRepository(ODatabaseDocumentTx db, ApplicationSettings applicationSettings, Identity identity) {
		super(db, applicationSettings, identity);
	}

	@Override
	public IPeriod convertToEntity(ODocument document) {
		Period period = new Period();
		period.setFromEntryCalendar(document.field("fromEntryCalendar", String.class));
		period.setToEntryCalendar(document.field("toEntryCalendar", String.class));
		period.setFromEntry(document.field("fromEntry", String.class));
		period.setToEntry(document.field("toEntry", String.class));
		period.setParentId(document.field("parentId", String.class));
		//type/from/to set automatically

		// populate with data
		populateEntityWithBaseData(document, period);
		populateEntityWithCreatedModified(document, period);

		// parent
		ORecordId parent = document.field("parent", ORecordId.class);
		if (parent != null) {
			initDb();

			period.setParentId(parent.getIdentity().toString());
			period.setParentModel(db.getMetadata().getSchema().getClassByClusterId(parent.getClusterId()).getName());
		}

		return period;
	}

	@Override
	public ODocument convertToDocument(IPeriod entity) {
		ODocument document = createOrLoadDocument(entity);

		// populate with data
		document.field("fromEntryCalendar", entity.getFromEntryCalendar())
				.field("toEntryCalendar", entity.getToEntryCalendar())
				.field("fromEntry", entity.getFromEntry())
				.field("toEntry", entity.getToEntry())
				.field("fromJD", entity.getFromJD())
				.field("toJD", entity.getToJD())
				.field("type", entity.getType())
				.field("parent", new ORecordId(entity.getParentId()));

		// populate with created/modified stuff
		populateODocumentWithCreatedModified(document, (Period) entity);

		return document;
	}

	@Override
	public String getModelClassName() {
		return "Period";
	}

	@Override
	public List<IPeriod> findByParent(String id) {
		if (id == null || "".equals(id)) return null;

		initDb();

		// execute query
		OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>("select @rid as id from Period where parent = ?" + getDefaultOrder(true));
		List<ODocument> result = db.command(query).execute(new ORecordId(id));

		List<IPeriod> list = new LinkedList<>();

		// populate set
		for (ODocument document : result)
			list.add(lazyLoadInstance(document.field("id", String.class)));

		return list;
	}

	/**
	 * lazy load instance
	 * @param id of entity
	 * @return lazy loading proxy for entity
	 */
	public IPeriod lazyLoadInstance(final String id) {
		final PeriodRepository repository = this;
		try {
			return (IPeriod) java.lang.reflect.Proxy.newProxyInstance(
					IPeriod.class.getClassLoader(),
					new Class[]{IPeriod.class},
					new AbstractLazyLoadedObject() {
						@Override
						protected Object loadObject() {
							return repository.find(id);
						}
					}
			);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public List<IPeriod> findWithin(Long jdStart, Long jdEnd) {
		// build query: -1000000 because Long.MIN_VALUE breaks query
		String constraints;
		if (jdStart != null && jdEnd != null) {
			constraints = " WHERE (toJD >= " + jdStart + " AND fromJD <= " + jdEnd + ") OR (fromJD < -1000000 AND toJD >= " + jdStart + ") OR (toJD = " + Long.MAX_VALUE + " AND fromJD <= " + jdEnd + ")";
		} else if (jdStart != null)
			constraints = " WHERE (fromJD >= " + jdStart + ") OR (fromJD < -1000000 AND toJD >= " + jdStart + ")";
		else if (jdEnd != null)
			constraints = " WHERE (toJD <= " + jdEnd + ") OR (toJD = " + Long.MAX_VALUE + " AND fromJD <= " + jdEnd + ")";
		else constraints = ""; // no constraints

		initDb();

		// execute query - maximum of 100 hits (hardcoded for now)
		OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>("select * from Period" + constraints + getDefaultOrder(true) + " LIMIT 100");
		List<ODocument> result = db.command(query).execute();

		List<IPeriod> list = new LinkedList<>();

		// populate set
		for (ODocument document : result)
			list.add(convertToEntity(document));

		return list;
	}

	@Override
	public List<IPeriod> findWithin(DateTime start, DateTime end) {
		// convert and call base method
		Long jdStart = null, jdEnd = null;

		if (start != null) jdStart = DateTimeUtils.toJulianDayNumber(start.getMillis());
		if (end != null) jdEnd = DateTimeUtils.toJulianDayNumber(end.getMillis());

		return findWithin(jdStart, jdEnd);
	}

	@Override
	protected String getDefaultOrder(boolean addOrderBy) {
		return (addOrderBy?" ORDER BY":"").concat(" fromJD ASC, toJD ASC");
	}
}
