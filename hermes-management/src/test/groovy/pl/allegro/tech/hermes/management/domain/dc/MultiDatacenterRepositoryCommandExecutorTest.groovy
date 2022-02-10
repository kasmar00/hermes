package pl.allegro.tech.hermes.management.domain.dc

import pl.allegro.tech.hermes.common.exception.InternalProcessingException
import pl.allegro.tech.hermes.common.exception.RepositoryNotAvailableException
import pl.allegro.tech.hermes.management.domain.auth.RequestUser
import spock.lang.Specification


class MultiDatacenterRepositoryCommandExecutorTest extends Specification {

    private static ADMIN = new RequestUser("ADMIN", true)
    private static NON_ADMIN = new RequestUser("USER", false)

    def "should execute backup if rollback is enabled"() {
        given:
        def executor = buildExecutor(true)
        def command = Mock(RepositoryCommand)

        when:
        executor.execute(command)

        then:
        1 * command.backup(_)
    }

    def "should not execute backup if rollback is disabled"() {
        given:
        def executor = buildExecutor(false)
        def command = Mock(RepositoryCommand)

        when:
        executor.execute(command)

        then:
        0 * command.backup(_)
    }

    def "should execute command on all repository holders"() {
        given:
        def holder1 = Stub(DatacenterBoundRepositoryHolder)
        def holder2 = Stub(DatacenterBoundRepositoryHolder)

        def executor = buildExecutor([holder1, holder2], true)

        def command = Mock(RepositoryCommand)

        when:
        executor.execute(command)

        then:
        1 * command.execute(holder1)
        1 * command.execute(holder2)
    }

    def "should rollback if execution failed on second holder"() {
        given:
        def holder1 = Stub(DatacenterBoundRepositoryHolder)
        def holder2 = Stub(DatacenterBoundRepositoryHolder)

        def executor = buildExecutor([holder1, holder2], true)

        def command = Mock(RepositoryCommand)
        command.execute(holder2) >> { throw new Exception() }

        when:
        executor.execute(command)

        then:
        1 * command.rollback(holder1)

        thrown InternalProcessingException
    }

    def "should not rollback if rollback is disabled"() {
        given:
        def holder1 = Stub(DatacenterBoundRepositoryHolder)
        def holder2 = Stub(DatacenterBoundRepositoryHolder)

        def executor = buildExecutor([holder1, holder2], false)

        def command = Mock(RepositoryCommand)
        command.execute(holder2) >> { throw new Exception() }

        when:
        executor.execute(command)

        then:
        0 * command.rollback(holder1)

        thrown InternalProcessingException
    }

    def "should not rollback and fail when executing user is admin"() {
        given:
        def holder1 = Stub(DatacenterBoundRepositoryHolder)
        def holder2 = Stub(DatacenterBoundRepositoryHolder)

        def executor = buildExecutor([holder1, holder2], false)

        def command = Mock(RepositoryCommand)
        command.execute(holder2) >> { throw new Exception() }

        when:
        executor.executeByUser(command, ADMIN)

        then:
        0 * command.rollback(holder1)

        thrown InternalProcessingException
    }

    def "should not rollback and not fail when executing user is admin and Zookeper node is broken"() {
        given:
        def holder1 = Stub(DatacenterBoundRepositoryHolder)
        def holder2 = Stub(DatacenterBoundRepositoryHolder)

        def executor = buildExecutor([holder1, holder2], true)

        def command = Mock(RepositoryCommand)
        command.execute(holder2) >> { throw new RepositoryNotAvailableException("") }

        when:
        executor.executeByUser(command, ADMIN)

        then:
        0 * command.rollback(holder1)
    }

    def "should use executor rollback and fail when executing user is not admin"() {
        given:
        def isRollbackEnabled = true
        def holder1 = Stub(DatacenterBoundRepositoryHolder)
        def holder2 = Stub(DatacenterBoundRepositoryHolder)

        def executor = buildExecutor([holder1, holder2], isRollbackEnabled)

        def command = Mock(RepositoryCommand)
        command.execute(holder2) >> { throw new Exception() }

        when:
        executor.executeByUser(command, NON_ADMIN)

        then:
        1 * command.rollback(holder1)

        thrown InternalProcessingException
    }

    private buildExecutor(boolean rollbackEnabled) {
        def repositoryManager = Stub(RepositoryManager)
        return new MultiDatacenterRepositoryCommandExecutor(repositoryManager, rollbackEnabled)
    }

    private buildExecutor(List dcHolders, boolean rollbackEnabled) {
        def repositoryManager = Stub(RepositoryManager)
        repositoryManager.getRepositories(_) >> dcHolders
        return new MultiDatacenterRepositoryCommandExecutor(repositoryManager, rollbackEnabled)
    }

}
