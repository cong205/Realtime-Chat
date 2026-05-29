CREATE DATABASE RealtimeChatProduction
GO

USE RealtimeChatProduction
GO

-- =========================================
-- USERS
-- =========================================
CREATE TABLE Users (
    UserID UNIQUEIDENTIFIER
        PRIMARY KEY DEFAULT NEWID(),

    Username NVARCHAR(50)
        NOT NULL UNIQUE,

    Email NVARCHAR(100)
        NOT NULL UNIQUE,

    PasswordHash NVARCHAR(255)
        NOT NULL,

    FullName NVARCHAR(100),

    AvatarUrl NVARCHAR(2000),

    Bio NVARCHAR(255),

    IsOnline BIT DEFAULT 0,

    LastSeen DATETIME2,

    IsDeleted BIT DEFAULT 0,

    CreatedAt DATETIME2
        DEFAULT SYSDATETIME(),

    UpdatedAt DATETIME2
        DEFAULT SYSDATETIME()
)

-- =========================================
-- REFRESH TOKENS
-- =========================================
CREATE TABLE RefreshTokens (
    TokenID UNIQUEIDENTIFIER
        PRIMARY KEY DEFAULT NEWID(),

    UserID UNIQUEIDENTIFIER NOT NULL,

    Token NVARCHAR(MAX) NOT NULL,

    ExpiryDate DATETIME2 NOT NULL,

    IsRevoked BIT DEFAULT 0,

    CreatedAt DATETIME2
        DEFAULT SYSDATETIME(),

    FOREIGN KEY (UserID)
        REFERENCES Users(UserID)
)

-- =========================================
-- CONVERSATIONS
-- =========================================
CREATE TABLE Conversations (
    ConversationID UNIQUEIDENTIFIER
        PRIMARY KEY DEFAULT NEWID(),

    ConversationName NVARCHAR(100),

    IsGroup BIT DEFAULT 0,

    CreatedBy UNIQUEIDENTIFIER NOT NULL,

    LastMessageID UNIQUEIDENTIFIER NULL,

    IsDeleted BIT DEFAULT 0,

    CreatedAt DATETIME2
        DEFAULT SYSDATETIME(),

    UpdatedAt DATETIME2
        DEFAULT SYSDATETIME(),

    FOREIGN KEY (CreatedBy)
        REFERENCES Users(UserID)
)

-- =========================================
-- CONVERSATION MEMBERS
-- =========================================
CREATE TABLE ConversationMembers (
    ConversationMemberID UNIQUEIDENTIFIER
        PRIMARY KEY DEFAULT NEWID(),

    ConversationID UNIQUEIDENTIFIER NOT NULL,

    UserID UNIQUEIDENTIFIER NOT NULL,

    RoleName NVARCHAR(20)
        DEFAULT 'member',

    Nickname NVARCHAR(100),

    JoinedAt DATETIME2
        DEFAULT SYSDATETIME(),

    LastReadMessageID UNIQUEIDENTIFIER NULL,

    IsMuted BIT DEFAULT 0,

    IsLeft BIT DEFAULT 0,

    FOREIGN KEY (ConversationID)
        REFERENCES Conversations(ConversationID),

    FOREIGN KEY (UserID)
        REFERENCES Users(UserID)
)

-- =========================================
-- MESSAGES
-- =========================================
CREATE TABLE Messages (
    MessageID UNIQUEIDENTIFIER
        PRIMARY KEY DEFAULT NEWID(),

    ConversationID UNIQUEIDENTIFIER NOT NULL,

    SenderID UNIQUEIDENTIFIER NOT NULL,

    MessageType NVARCHAR(20)
        DEFAULT 'text',

    Content NVARCHAR(MAX),

    ReplyToMessageID UNIQUEIDENTIFIER NULL,

    IsEdited BIT DEFAULT 0,

    IsDeleted BIT DEFAULT 0,

    DeletedAt DATETIME2 NULL,

    SentAt DATETIME2
        DEFAULT SYSDATETIME(),

    FOREIGN KEY (ConversationID)
        REFERENCES Conversations(ConversationID),

    FOREIGN KEY (SenderID)
        REFERENCES Users(UserID),

    FOREIGN KEY (ReplyToMessageID)
        REFERENCES Messages(MessageID)
)

-- =========================================
-- MESSAGE ATTACHMENTS
-- =========================================
CREATE TABLE MessageAttachments (
    AttachmentID UNIQUEIDENTIFIER
        PRIMARY KEY DEFAULT NEWID(),

    MessageID UNIQUEIDENTIFIER NOT NULL,

    FileName NVARCHAR(255),

    FileUrl NVARCHAR(2000),

    FileType NVARCHAR(50),

    MimeType NVARCHAR(100),

    FileSize BIGINT,

    UploadedAt DATETIME2
        DEFAULT SYSDATETIME(),

    FOREIGN KEY (MessageID)
        REFERENCES Messages(MessageID)
)

-- =========================================
-- MESSAGE REACTIONS
-- =========================================
CREATE TABLE MessageReactions (
    ReactionID UNIQUEIDENTIFIER
        PRIMARY KEY DEFAULT NEWID(),

    MessageID UNIQUEIDENTIFIER NOT NULL,

    UserID UNIQUEIDENTIFIER NOT NULL,

    ReactionType NVARCHAR(20),

    CreatedAt DATETIME2
        DEFAULT SYSDATETIME(),

    FOREIGN KEY (MessageID)
        REFERENCES Messages(MessageID),

    FOREIGN KEY (UserID)
        REFERENCES Users(UserID)
)

-- =========================================
-- MESSAGE STATUS
-- =========================================
CREATE TABLE MessageStatus (
    ID BIGINT IDENTITY(1,1)
        PRIMARY KEY,

    MessageID UNIQUEIDENTIFIER NOT NULL,

    UserID UNIQUEIDENTIFIER NOT NULL,

    DeliveredAt DATETIME2 NULL,

    SeenAt DATETIME2 NULL,

    FOREIGN KEY (MessageID)
        REFERENCES Messages(MessageID),

    FOREIGN KEY (UserID)
        REFERENCES Users(UserID)
)

-- =========================================
-- FRIENDSHIPS
-- =========================================
CREATE TABLE Friendships (
    FriendshipID UNIQUEIDENTIFIER
        PRIMARY KEY DEFAULT NEWID(),

    User1 UNIQUEIDENTIFIER NOT NULL,

    User2 UNIQUEIDENTIFIER NOT NULL,

    Status NVARCHAR(20)
        DEFAULT 'pending',

    ActionBy UNIQUEIDENTIFIER,

    CreatedAt DATETIME2
        DEFAULT SYSDATETIME(),

    FOREIGN KEY (User1)
        REFERENCES Users(UserID),

    FOREIGN KEY (User2)
        REFERENCES Users(UserID),

    FOREIGN KEY (ActionBy)
        REFERENCES Users(UserID)
)

-- =========================================
-- NOTIFICATIONS
-- =========================================
CREATE TABLE Notifications (
    NotificationID UNIQUEIDENTIFIER
        PRIMARY KEY DEFAULT NEWID(),

    UserID UNIQUEIDENTIFIER NOT NULL,

    Title NVARCHAR(255),

    Content NVARCHAR(MAX),

    IsRead BIT DEFAULT 0,

    CreatedAt DATETIME2
        DEFAULT SYSDATETIME(),

    FOREIGN KEY (UserID)
        REFERENCES Users(UserID)
)

-- =========================================
-- INDEXES
-- =========================================
CREATE NONCLUSTERED INDEX IX_Messages_Conversation_SentAt
ON Messages(ConversationID, SentAt DESC)

CREATE NONCLUSTERED INDEX IX_ConversationMembers_UserID
ON ConversationMembers(UserID)

CREATE NONCLUSTERED INDEX IX_MessageStatus_Message_User
ON MessageStatus(MessageID, UserID)

CREATE NONCLUSTERED INDEX IX_Users_Online
ON Users(IsOnline)

-- =========================================
-- STORED PROCEDURE SEND MESSAGE
-- =========================================
GO

CREATE PROCEDURE sp_SendMessage
(
    @ConversationID UNIQUEIDENTIFIER,
    @SenderID UNIQUEIDENTIFIER,
    @Content NVARCHAR(MAX),
    @MessageType NVARCHAR(20) = 'text'
)
AS
BEGIN
    SET NOCOUNT ON

    DECLARE @MessageID UNIQUEIDENTIFIER = NEWID()

    BEGIN TRANSACTION

    INSERT INTO Messages (
        MessageID,
        ConversationID,
        SenderID,
        Content,
        MessageType
    )
    VALUES (
        @MessageID,
        @ConversationID,
        @SenderID,
        @Content,
        @MessageType
    )

    UPDATE Conversations
    SET
        LastMessageID = @MessageID,
        UpdatedAt = SYSDATETIME()
    WHERE ConversationID = @ConversationID

    COMMIT TRANSACTION

    SELECT @MessageID AS MessageID
END

GO

-- =========================================
-- LOAD MESSAGES
-- =========================================
CREATE PROCEDURE sp_LoadMessages
(
    @ConversationID UNIQUEIDENTIFIER,
    @Page INT = 1,
    @PageSize INT = 20
)
AS
BEGIN
    SET NOCOUNT ON

    SELECT *
    FROM Messages
    WHERE ConversationID = @ConversationID
      AND IsDeleted = 0
    ORDER BY SentAt DESC
    OFFSET (@Page - 1) * @PageSize ROWS
    FETCH NEXT @PageSize ROWS ONLY
END

GO

-- =========================================
-- TRIGGER UPDATE CONVERSATION TIME
-- =========================================
CREATE TRIGGER trg_UpdateConversationTime
ON Messages
AFTER INSERT
AS
BEGIN
    UPDATE c
    SET UpdatedAt = SYSDATETIME()
    FROM Conversations c
    INNER JOIN inserted i
        ON c.ConversationID = i.ConversationID
END
GO

