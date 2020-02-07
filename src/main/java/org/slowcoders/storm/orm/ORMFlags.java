package org.slowcoders.storm.orm;

public interface ORMFlags {

	/**
	 * The property is immutable.
	 */
	int Immutable = 0x01;

	/**
	 * The property should be sync with server.
	 */
	int SyncUpdate = 0x02;
	
	/* internal */ int Volatile = 0x04;
	

	/****************************************/
	/*  NOT NULL constraint                 */
	/*--------------------------------------*/
//	/**
//	 * The property value can not be null.
//	 */
//	int NotNull = 0x08;

	int Nullable = 0x08;

	/****************************************/
	/*  Unique constraints                  */
	/*--------------------------------------*/
	/**
	 * The property is unique. Also it can not be null.
	 */
	int Unique = 0x10;

	/**
	 * The property is unique and caches search results internally. Also it can not be null.
	 */
	int Unique_Cache = Unique;// | 0x20;

	
	/**
	 * The property is indexed.
	 */
	int Indexed = 0x40;

	/**
	 * The property is indexed.
	 */
	int CanOverride = 0x80;

	/**
	 * The property should not be exported.
	 */
	int Hidden = 0x100;
	
	int ReadOnly = 0x200;

	int Virtual = 0x400;
	
	int Embedded = 0x800;


	int FTS_RAW = 0x1000;
	int FTS_Normalize = 0x2000;

	int Reserved_1 = 0x4000;

	int UnsafeMutable = 0x8000;

	int NineLocalProperty = 0;

}
