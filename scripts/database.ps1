param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("initialize", "update", "stop", "drop")]
    [string] $Action
)

$ErrorActionPreference = "Stop"

function Invoke-Compose {
    & docker compose @args
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed with exit code $LASTEXITCODE."
    }
}

function Invoke-DatabaseSql {
    param([string] $Sql)

    $result = & docker compose exec -T db mysql -N -B -u todo_user -ptodo_password todo_db -e $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "Database command failed with exit code $LASTEXITCODE."
    }
    $result
}

function Invoke-Migration {
    param([string] $Path)

    $sql = Get-Content -Raw $Path
    Invoke-DatabaseSql $sql
}

switch ($Action) {
    "initialize" {
        Invoke-Compose up -d db mysql-gui
    }
    "update" {
        Invoke-Compose up -d --wait db

        $scheduleTables = Invoke-DatabaseSql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('schedules', 'schedule_tasks')"
        if ($scheduleTables -eq "0") {
            Invoke-Migration "docker/mysql/migrations/002_user_schedules.sql"
        } elseif ($scheduleTables -ne "2") {
            throw "Only one schedule table exists. Inspect the database before applying migrations; no migration was run."
        }

        $legacyRoleColumn = Invoke-DatabaseSql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'user_role'"
        if ($legacyRoleColumn -eq "1") {
            Invoke-Migration "docker/mysql/migrations/003_tomcat_realm_roles.sql"
        } elseif ($legacyRoleColumn -eq "0") {
            $roleTable = Invoke-DatabaseSql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'user_roles'"
            if ($roleTable -ne "1") {
                throw "The legacy role column and user_roles table are both missing. Inspect the database before continuing."
            }
        } else {
            throw "Unexpected users.user_role column state; no role migration was run."
        }

        $taskOwnerColumn = Invoke-DatabaseSql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tasks' AND column_name = 'user_id'"
        if ($taskOwnerColumn -eq "0") {
            $adminAccount = Invoke-DatabaseSql "SELECT COUNT(*) FROM users WHERE username = 'admin'"
            if ($adminAccount -ne "1") {
                throw "The seeded admin account is required to own existing tasks. No task ownership migration was run."
            }
            Invoke-Migration "docker/mysql/migrations/004_account_task_ownership.sql"
        } elseif ($taskOwnerColumn -ne "1") {
            throw "Unexpected tasks.user_id column state; no ownership migration was run."
        } else {
            $ownerColumnNullable = Invoke-DatabaseSql "SELECT is_nullable FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tasks' AND column_name = 'user_id'"
            if ($ownerColumnNullable -ne "NO") {
                throw "The tasks.user_id migration is incomplete. Inspect the database before retrying."
            }
        }
        Invoke-Migration "docker/mysql/migrations/005_admin_default_credentials.sql"
        Write-Host "Database migrations are up to date."
    }
    "stop" {
        Invoke-Compose down
    }
    "drop" {
        $confirmation = Read-Host "This stops the Compose services and permanently deletes their named volumes, including all todo_db data. Type 'todo_db' to confirm"
        if ($confirmation -cne "todo_db") {
            Write-Host "Cancelled; the database was not changed."
            break
        }

        Invoke-Compose down --volumes
    }
}
