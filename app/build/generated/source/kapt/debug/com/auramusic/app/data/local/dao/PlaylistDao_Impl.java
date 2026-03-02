package com.auramusic.app.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.RelationUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.auramusic.app.data.local.entity.PlaylistEntity;
import com.auramusic.app.data.local.entity.PlaylistSongCrossRef;
import com.auramusic.app.data.local.entity.PlaylistWithSongs;
import com.auramusic.app.data.local.entity.SongEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@SuppressWarnings({"unchecked", "deprecation"})
public final class PlaylistDao_Impl implements PlaylistDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PlaylistEntity> __insertionAdapterOfPlaylistEntity;

  private final EntityInsertionAdapter<PlaylistSongCrossRef> __insertionAdapterOfPlaylistSongCrossRef;

  private final SharedSQLiteStatement __preparedStmtOfDeletePlaylist;

  private final SharedSQLiteStatement __preparedStmtOfRemoveSongFromPlaylist;

  public PlaylistDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPlaylistEntity = new EntityInsertionAdapter<PlaylistEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `playlists` (`playlistId`,`name`,`coverUrl`,`createdAt`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PlaylistEntity entity) {
        statement.bindLong(1, entity.getPlaylistId());
        if (entity.getName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getName());
        }
        if (entity.getCoverUrl() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCoverUrl());
        }
        statement.bindLong(4, entity.getCreatedAt());
      }
    };
    this.__insertionAdapterOfPlaylistSongCrossRef = new EntityInsertionAdapter<PlaylistSongCrossRef>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `playlist_song_cross_ref` (`playlistId`,`songId`,`addedAt`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PlaylistSongCrossRef entity) {
        statement.bindLong(1, entity.getPlaylistId());
        if (entity.getSongId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getSongId());
        }
        statement.bindLong(3, entity.getAddedAt());
      }
    };
    this.__preparedStmtOfDeletePlaylist = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM playlists WHERE playlistId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfRemoveSongFromPlaylist = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM playlist_song_cross_ref WHERE playlistId = ? AND songId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object createPlaylist(final PlaylistEntity playlist,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfPlaylistEntity.insertAndReturnId(playlist);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object addSongToPlaylist(final PlaylistSongCrossRef crossRef,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPlaylistSongCrossRef.insert(crossRef);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deletePlaylist(final long playlistId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeletePlaylist.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, playlistId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeletePlaylist.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object removeSongFromPlaylist(final long playlistId, final String songId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRemoveSongFromPlaylist.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, playlistId);
        _argIndex = 2;
        if (songId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, songId);
        }
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRemoveSongFromPlaylist.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<PlaylistEntity>> getAllPlaylists() {
    final String _sql = "SELECT * FROM playlists ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"playlists"}, new Callable<List<PlaylistEntity>>() {
      @Override
      @NonNull
      public List<PlaylistEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPlaylistId = CursorUtil.getColumnIndexOrThrow(_cursor, "playlistId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfCoverUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "coverUrl");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<PlaylistEntity> _result = new ArrayList<PlaylistEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PlaylistEntity _item;
            final long _tmpPlaylistId;
            _tmpPlaylistId = _cursor.getLong(_cursorIndexOfPlaylistId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpCoverUrl;
            if (_cursor.isNull(_cursorIndexOfCoverUrl)) {
              _tmpCoverUrl = null;
            } else {
              _tmpCoverUrl = _cursor.getString(_cursorIndexOfCoverUrl);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new PlaylistEntity(_tmpPlaylistId,_tmpName,_tmpCoverUrl,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<PlaylistWithSongs> getPlaylistWithSongs(final long playlistId) {
    final String _sql = "SELECT * FROM playlists WHERE playlistId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, playlistId);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"playlist_song_cross_ref", "songs",
        "playlists"}, new Callable<PlaylistWithSongs>() {
      @Override
      @Nullable
      public PlaylistWithSongs call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfPlaylistId = CursorUtil.getColumnIndexOrThrow(_cursor, "playlistId");
            final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
            final int _cursorIndexOfCoverUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "coverUrl");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final LongSparseArray<ArrayList<SongEntity>> _collectionSongs = new LongSparseArray<ArrayList<SongEntity>>();
            while (_cursor.moveToNext()) {
              final long _tmpKey;
              _tmpKey = _cursor.getLong(_cursorIndexOfPlaylistId);
              if (!_collectionSongs.containsKey(_tmpKey)) {
                _collectionSongs.put(_tmpKey, new ArrayList<SongEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipsongsAscomAuramusicAppDataLocalEntitySongEntity(_collectionSongs);
            final PlaylistWithSongs _result;
            if (_cursor.moveToFirst()) {
              final PlaylistEntity _tmpPlaylist;
              final long _tmpPlaylistId;
              _tmpPlaylistId = _cursor.getLong(_cursorIndexOfPlaylistId);
              final String _tmpName;
              if (_cursor.isNull(_cursorIndexOfName)) {
                _tmpName = null;
              } else {
                _tmpName = _cursor.getString(_cursorIndexOfName);
              }
              final String _tmpCoverUrl;
              if (_cursor.isNull(_cursorIndexOfCoverUrl)) {
                _tmpCoverUrl = null;
              } else {
                _tmpCoverUrl = _cursor.getString(_cursorIndexOfCoverUrl);
              }
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              _tmpPlaylist = new PlaylistEntity(_tmpPlaylistId,_tmpName,_tmpCoverUrl,_tmpCreatedAt);
              final ArrayList<SongEntity> _tmpSongsCollection;
              final long _tmpKey_1;
              _tmpKey_1 = _cursor.getLong(_cursorIndexOfPlaylistId);
              _tmpSongsCollection = _collectionSongs.get(_tmpKey_1);
              _result = new PlaylistWithSongs(_tmpPlaylist,_tmpSongsCollection);
            } else {
              _result = null;
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<PlaylistWithSongs>> getAllPlaylistsWithSongs() {
    final String _sql = "SELECT * FROM playlists ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"playlist_song_cross_ref", "songs",
        "playlists"}, new Callable<List<PlaylistWithSongs>>() {
      @Override
      @NonNull
      public List<PlaylistWithSongs> call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfPlaylistId = CursorUtil.getColumnIndexOrThrow(_cursor, "playlistId");
            final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
            final int _cursorIndexOfCoverUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "coverUrl");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final LongSparseArray<ArrayList<SongEntity>> _collectionSongs = new LongSparseArray<ArrayList<SongEntity>>();
            while (_cursor.moveToNext()) {
              final long _tmpKey;
              _tmpKey = _cursor.getLong(_cursorIndexOfPlaylistId);
              if (!_collectionSongs.containsKey(_tmpKey)) {
                _collectionSongs.put(_tmpKey, new ArrayList<SongEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipsongsAscomAuramusicAppDataLocalEntitySongEntity(_collectionSongs);
            final List<PlaylistWithSongs> _result = new ArrayList<PlaylistWithSongs>(_cursor.getCount());
            while (_cursor.moveToNext()) {
              final PlaylistWithSongs _item;
              final PlaylistEntity _tmpPlaylist;
              final long _tmpPlaylistId;
              _tmpPlaylistId = _cursor.getLong(_cursorIndexOfPlaylistId);
              final String _tmpName;
              if (_cursor.isNull(_cursorIndexOfName)) {
                _tmpName = null;
              } else {
                _tmpName = _cursor.getString(_cursorIndexOfName);
              }
              final String _tmpCoverUrl;
              if (_cursor.isNull(_cursorIndexOfCoverUrl)) {
                _tmpCoverUrl = null;
              } else {
                _tmpCoverUrl = _cursor.getString(_cursorIndexOfCoverUrl);
              }
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              _tmpPlaylist = new PlaylistEntity(_tmpPlaylistId,_tmpName,_tmpCoverUrl,_tmpCreatedAt);
              final ArrayList<SongEntity> _tmpSongsCollection;
              final long _tmpKey_1;
              _tmpKey_1 = _cursor.getLong(_cursorIndexOfPlaylistId);
              _tmpSongsCollection = _collectionSongs.get(_tmpKey_1);
              _item = new PlaylistWithSongs(_tmpPlaylist,_tmpSongsCollection);
              _result.add(_item);
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private void __fetchRelationshipsongsAscomAuramusicAppDataLocalEntitySongEntity(
      @NonNull final LongSparseArray<ArrayList<SongEntity>> _map) {
    if (_map.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchLongSparseArray(_map, true, (map) -> {
        __fetchRelationshipsongsAscomAuramusicAppDataLocalEntitySongEntity(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `songs`.`id` AS `id`,`songs`.`title` AS `title`,`songs`.`artist` AS `artist`,`songs`.`thumbnailUrl` AS `thumbnailUrl`,`songs`.`durationSeconds` AS `durationSeconds`,`songs`.`streamUrl` AS `streamUrl`,`songs`.`genre` AS `genre`,`songs`.`isFavorite` AS `isFavorite`,`songs`.`likedAt` AS `likedAt`,`songs`.`lastPlayedAt` AS `lastPlayedAt`,_junction.`playlistId` FROM `playlist_song_cross_ref` AS _junction INNER JOIN `songs` ON (_junction.`songId` = `songs`.`id`) WHERE _junction.`playlistId` IN (");
    final int _inputSize = _map.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (int i = 0; i < _map.size(); i++) {
      final long _item = _map.keyAt(i);
      _stmt.bindLong(_argIndex, _item);
      _argIndex++;
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      // _junction.playlistId;
      final int _itemKeyIndex = 10;
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfId = 0;
      final int _cursorIndexOfTitle = 1;
      final int _cursorIndexOfArtist = 2;
      final int _cursorIndexOfThumbnailUrl = 3;
      final int _cursorIndexOfDurationSeconds = 4;
      final int _cursorIndexOfStreamUrl = 5;
      final int _cursorIndexOfGenre = 6;
      final int _cursorIndexOfIsFavorite = 7;
      final int _cursorIndexOfLikedAt = 8;
      final int _cursorIndexOfLastPlayedAt = 9;
      while (_cursor.moveToNext()) {
        final long _tmpKey;
        _tmpKey = _cursor.getLong(_itemKeyIndex);
        final ArrayList<SongEntity> _tmpRelation = _map.get(_tmpKey);
        if (_tmpRelation != null) {
          final SongEntity _item_1;
          final String _tmpId;
          if (_cursor.isNull(_cursorIndexOfId)) {
            _tmpId = null;
          } else {
            _tmpId = _cursor.getString(_cursorIndexOfId);
          }
          final String _tmpTitle;
          if (_cursor.isNull(_cursorIndexOfTitle)) {
            _tmpTitle = null;
          } else {
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
          }
          final String _tmpArtist;
          if (_cursor.isNull(_cursorIndexOfArtist)) {
            _tmpArtist = null;
          } else {
            _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
          }
          final String _tmpThumbnailUrl;
          if (_cursor.isNull(_cursorIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null;
          } else {
            _tmpThumbnailUrl = _cursor.getString(_cursorIndexOfThumbnailUrl);
          }
          final long _tmpDurationSeconds;
          _tmpDurationSeconds = _cursor.getLong(_cursorIndexOfDurationSeconds);
          final String _tmpStreamUrl;
          if (_cursor.isNull(_cursorIndexOfStreamUrl)) {
            _tmpStreamUrl = null;
          } else {
            _tmpStreamUrl = _cursor.getString(_cursorIndexOfStreamUrl);
          }
          final String _tmpGenre;
          if (_cursor.isNull(_cursorIndexOfGenre)) {
            _tmpGenre = null;
          } else {
            _tmpGenre = _cursor.getString(_cursorIndexOfGenre);
          }
          final boolean _tmpIsFavorite;
          final int _tmp;
          _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
          _tmpIsFavorite = _tmp != 0;
          final Long _tmpLikedAt;
          if (_cursor.isNull(_cursorIndexOfLikedAt)) {
            _tmpLikedAt = null;
          } else {
            _tmpLikedAt = _cursor.getLong(_cursorIndexOfLikedAt);
          }
          final Long _tmpLastPlayedAt;
          if (_cursor.isNull(_cursorIndexOfLastPlayedAt)) {
            _tmpLastPlayedAt = null;
          } else {
            _tmpLastPlayedAt = _cursor.getLong(_cursorIndexOfLastPlayedAt);
          }
          _item_1 = new SongEntity(_tmpId,_tmpTitle,_tmpArtist,_tmpThumbnailUrl,_tmpDurationSeconds,_tmpStreamUrl,_tmpGenre,_tmpIsFavorite,_tmpLikedAt,_tmpLastPlayedAt);
          _tmpRelation.add(_item_1);
        }
      }
    } finally {
      _cursor.close();
    }
  }
}
