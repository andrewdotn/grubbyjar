require 'pathname'

require_relative '../src/main/resources/ca/neitsch/grubbyjar/determine_gem_files'

def test_file(filename)
  Pathname.new(__FILE__).parent.parent / 'src' / 'test' / 'resources' /
      'ca' / 'neitsch' / 'grubbyjar' / filename
end

RSpec.describe '#determine_gem_files' do
  let(:ggem) { Java::CaNeitschGrubbyjar::Gem }
  
  it 'works' do
    gemfile = test_file('concurrent-ruby.Gemfile')
    gemfile_lock = test_file('concurrent-ruby.Gemfile.lock')

    expect(determine_gem_files(gemfile, gemfile_lock)).to contain_exactly(
        include(ggem::NAME => "bundler"),
        include(ggem::NAME => "concurrent-ruby", "version" => "1.0.4",
            ggem::INSTALL_PATH => match('/gems/concurrent-ruby-1.0.4')))
  end

  it 'gets executables from gemspecs' do
    gemfile = test_file('gemspec1/Gemfile')
    gemfile_lock = test_file('gemspec1/Gemfile.lock')

    expect(determine_gem_files(gemfile, gemfile_lock)).to contain_exactly(
        include(ggem::NAME => "bundler"),
        include(ggem::NAME => "concurrent-ruby", "version" => "1.0.4",
            ggem::INSTALL_PATH => match('/gems/concurrent-ruby-1.0.4')),
        hash_including(ggem::NAME => "gemspec1",
            ggem::EXECUTABLE => "bin/gemspec1",
            ggem::SPEC_TEXT => include("stub: gemspec1")))
  end
end
